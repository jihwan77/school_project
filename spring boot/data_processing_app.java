package data_processcing;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SpringBootApplication
@Service
@EnableScheduling
public class data_processing {
    @Value("${aws.bucket.name}")
    private String bucketName;

    private final String mqttBroker = "tcp://emqx-listener.mqtt:1883";
    private final String mqttTopic = "test";
    private final String mqttTopic2 = "test1";

    private MqttClient mqttClient;
    private S3Client s3Client;

    // 버퍼링 관련
    private final List<String> buffer = Collections.synchronizedList(new ArrayList<>());
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_MS = 60_000; // 1분
    private static final int MAX_BUFFER_BYTES = 1024 * 1024; // 예: 1MB
    private int currentBufferBytes = 0;
    private volatile long lastFlushTime = System.currentTimeMillis();

    public data_processing() {
        try {
            System.out.println("[1] MQTT 클라이언트 생성 중...");
            this.mqttClient = new MqttClient(mqttBroker, MqttClient.generateClientId());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            System.out.println("[2] MQTT 브로커에 연결 시도: " + mqttBroker);
            this.mqttClient.connect(options);
            System.out.println("[3] MQTT 연결 성공 ");

// S3 클라이언트 생성 (환경 변수 기반 인증)
            this.s3Client = S3Client.builder()
                    .region(Region.of(System.getenv("AWS_REGION")))
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .build();

        } catch (MqttException e) {
            System.out.println("[X] MQTT 연결 실패 : " + e.getMessage());
            e.printStackTrace();
        }
    }

    @KafkaListener(topics = "mqtt", groupId = "my-connect-consumer")
    public void consumeAndPublish(ConsumerRecord<String, byte[]> record) {
        try {
            System.out.println("[4] Kafka 메시지 수신됨. partition=" + record.partition() + ", offset=" + record.offset());
            String input = new String(record.value());
            System.out.println("[5] Kafka 메시지 내용: " + input);

            String processed = input.toUpperCase();
            System.out.println("[6] 처리된 메시지: " + processed);

            if (mqttClient == null || !mqttClient.isConnected()) {
                System.out.println("[X] MQTT 클라이언트가 연결되지 않았습니다. 재연결 시도 중...");
                mqttClient.reconnect();
                System.out.println("[✓] MQTT 재연결 성공");
            }

            MqttMessage message = new MqttMessage(processed.getBytes(StandardCharsets.UTF_8));
            message.setQos(0);

            System.out.println("[7] MQTT 메시지 발행 중 → 토픽: " + mqttTopic2);
            mqttClient.publish(mqttTopic2, message);
            System.out.println("[8] MQTT 메시지 발행 완료 ");

            int messageBytes = processed.getBytes(StandardCharsets.UTF_8).length;
            synchronized (buffer) {
                buffer.add(processed);
                currentBufferBytes += messageBytes;
            }
            if (buffer.size() >= BATCH_SIZE || currentBufferBytes >= MAX_BUFFER_BYTES) {
                flushBuffer();
            }

        } catch (Exception e) {
            System.out.println("[X] 메시지 처리 중 예외 발생 : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** 버퍼에 모인 메시지를 S3에 일괄 업로드 */
    private void flushBuffer() {
        List<String> toUpload;
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return;
            }
            toUpload = new ArrayList<>(buffer);
            buffer.clear();
            currentBufferBytes = 0; // 플러시 후 바이트 카운터 초기화
        }

        String combined = String.join("\n", toUpload);
        String key = mqttTopic + "/" + System.currentTimeMillis() + ".txt";

        try {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.putObject(req, software.amazon.awssdk.core.sync.RequestBody.fromString(combined));
            System.out.printf(" S3 일괄 업로드 완료: s3://%s/%s (%d 건)%n", bucketName, key, toUpload.size());
        } catch (S3Exception e) {
            System.err.println("⚠ S3 일괄 업로드 실패: " + e.getMessage());
        }

        lastFlushTime = System.currentTimeMillis();
    }

    /**
     * 주기적으로 버퍼를 확인하여 일정 시간 경과 시 플러시
     */
    @Scheduled(fixedDelayString = "${batch.flush-interval:60000}")
    public void scheduledFlush() {
        if (buffer.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFlushTime >= FLUSH_INTERVAL_MS) {
            System.out.println(" 스케줄러 기반 강제 플러시 실행");
            flushBuffer();
        }
    }

    /**
     * 애플리케이션 종료 시 버퍼에 남은 데이터를 S3에 업로드
     */
    @PreDestroy
    public void onShutdown() {
        System.out.println(" 애플리케이션 종료 시 버퍼 플러시 실행");
        flushBuffer();
    }

    public static void main(String[] args) {
        SpringApplication.run(data_processing.class, args);
    }
}
