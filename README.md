
# Kubernetes 기반 IoT 데이터 파이프라인 프로젝트

## 1. 프로젝트 개요

본 프로젝트는 Kubernetes 환경에서 MQTT, Kafka, Spring Boot, AWS S3를 연동하여 IoT 메시지 수집 및 처리 파이프라인을 구성한 프로젝트입니다.

MQTT Broker 역할은 EMQX가 담당하고, Kafka Connect는 MQTT Source Connector를 통해 MQTT 메시지를 Kafka Topic으로 전달합니다. 이후 Spring Boot 기반 데이터 처리 애플리케이션이 Kafka 메시지를 소비하여 데이터를 가공하고, 처리 결과를 다시 MQTT로 발행하거나 AWS S3에 배치 저장하도록 구성했습니다.

전체 구성은 Kubernetes YAML 파일을 중심으로 배포되며, 각 구성 요소는 디렉터리별로 분리되어 있습니다.



```text
MQTT Client
   |
   v
EMQX MQTT Broker
   |
   v
Kafka Connect MQTT Source Connector
   |
   v
Kafka Topic
   |
   v
Spring Boot Data Processing App
   |
   |-- Processed MQTT Publish
   |
   └── AWS S3 Batch Upload
```

---

## 2. 프로젝트 목표

* Kubernetes 기반 메시지 처리 시스템 구성
* MQTT Broker 클러스터 구성
* Kafka 기반 메시지 스트리밍 구조 구현
* Kafka Connect를 이용한 MQTT → Kafka 데이터 연동
* Spring Boot 애플리케이션을 통한 Kafka 메시지 처리
* 처리된 데이터를 MQTT로 재발행
* 처리 데이터를 AWS S3에 배치 저장
* HPA를 이용한 애플리케이션 Auto Scaling 구성
* Kubernetes YAML 기반 인프라 구성 관리

---

## 3. 주요 기술 스택

| 구분                      | 기술                                         |
| ----------------------- | ------------------------------------------ |
| Container Orchestration | Kubernetes                                 |
| MQTT Broker             | EMQX                                       |
| Message Streaming       | Apache Kafka                               |
| Kafka Connector         | Kafka Connect, Camel MQTT Source Connector |
| Application             | Spring Boot                                |
| Language                | Java                                       |
| Storage                 | AWS S3                                     |
| Network / Proxy         | HAProxy                                    |
| CNI                     | Calico                                     |
| Monitoring              | Kubernetes Monitoring YAML                 |
| Deployment              | Kubernetes Manifest, Helm Chart            |

---

## 4. 전체 아키텍처

![image](https://github.com/user-attachments/assets/de42d24e-730b-46ba-8615-ec6bc43b8618)

```text
[MQTT Publisher]
      |
      | MQTT Publish
      v
[EMQX MQTT Cluster]
      |
      | MQTT Topic: test
      v
[Kafka Connect]
      |
      | MQTT Source Connector
      v
[Kafka Topic: mqtt]
      |
      | Kafka Consumer
      v
[Spring Boot Data Processing App]
      |
      | 1. 데이터 가공
      | 2. MQTT Topic 재발행
      | 3. S3 배치 업로드
      v
[AWS S3]
```

### 데이터 흐름

1. MQTT Client가 EMQX Broker의 `test` 토픽으로 메시지를 발행합니다.
2. Kafka Connect의 MQTT Source Connector가 EMQX에서 메시지를 구독합니다.
3. Connector는 수신한 MQTT 메시지를 Kafka의 `mqtt` 토픽으로 전달합니다.
4. Spring Boot 애플리케이션은 Kafka `mqtt` 토픽을 소비합니다.
5. 애플리케이션은 메시지를 가공한 뒤 MQTT `test1` 토픽으로 재발행합니다.
6. 일정 개수 또는 일정 크기 이상 메시지가 누적되면 AWS S3에 배치 업로드합니다.

---

## 5. 디렉터리 구조

```text
school_project/
├── aws-s3/
│   └── aws-secret.yaml
│
├── calico-3.26.4/
│   ├── calico/
│   ├── calico-custom.yaml
│   └── calico.yaml
│
├── emqx/
│   ├── allow-coredns-tcp53.yaml
│   ├── emqx-core-k8s.yaml
│   ├── emqx-dashboard.yaml
│   ├── emqx-headless.yaml
│   ├── emqx-k8s-rbac.yaml
│   ├── emqx-listener.yaml
│   ├── emqx-replicant-hpa.yaml
│   └── emqx-replicant-k8s.yaml
│
├── haproxy/
│   ├── haproxy-configmap.yaml
│   ├── haproxy-deploy.yaml
│   └── haproxy-service.yaml
│
├── kafka/
│   ├── kafka-connect.yaml
│   ├── kafka-values.yaml
│   ├── mqtt-connector.yaml
│   └── mqttx-cli-linux-x64
│
├── monitoring/
│   └── ground/
│       └── k8s-1.27/
│
├── mqtt-cluster/
│   ├── templates/
│   ├── Chart.yaml
│   └── values.yaml
│
├── snap/
│
├── spring boot/
│   ├── app_hpa.yaml
│   ├── app_service.yaml
│   ├── data_processing_app.java
│   └── replicant_app.yaml
│
└── README.md
```

---

## 6. 구성 요소 설명

## 6.1 EMQX MQTT Broker

`emqx/` 디렉터리에는 Kubernetes 환경에서 EMQX MQTT Broker를 배포하기 위한 YAML 파일들이 포함되어 있습니다.

주요 파일은 다음과 같습니다.

| 파일                         | 설명                                     |
| -------------------------- | -------------------------------------- |
| `emqx-core-k8s.yaml`       | EMQX Core Node StatefulSet             |
| `emqx-replicant-k8s.yaml`  | EMQX Replicant Node 배포                 |
| `emqx-headless.yaml`       | EMQX 클러스터 내부 DNS 탐색용 Headless Service  |
| `emqx-listener.yaml`       | MQTT Broker 외부/내부 접속용 Service          |
| `emqx-dashboard.yaml`      | EMQX Dashboard 접속용 Service             |
| `emqx-k8s-rbac.yaml`       | Kubernetes 기반 EMQX 클러스터 디스커버리를 위한 RBAC |
| `emqx-replicant-hpa.yaml`  | Replicant Node Auto Scaling 설정         |
| `allow-coredns-tcp53.yaml` | DNS 통신 허용을 위한 네트워크 정책                  |

EMQX Core Node는 StatefulSet으로 구성되어 있으며, Kubernetes Service Discovery를 사용해 클러스터를 구성합니다.

```text
EMQX_CLUSTER__DISCOVERY_STRATEGY = k8s
EMQX_CLUSTER__K8S__NAMESPACE = mqtt
EMQX_CLUSTER__K8S__SERVICE_NAME = emqx-headless
```

---

## 6.2 Kafka

`kafka/` 디렉터리에는 Kafka 및 Kafka Connect 관련 설정이 포함되어 있습니다.

| 파일                    | 설명                             |
| --------------------- | ------------------------------ |
| `kafka-values.yaml`   | Kafka Helm 배포용 values 파일       |
| `kafka-connect.yaml`  | Strimzi KafkaConnect 리소스       |
| `mqtt-connector.yaml` | MQTT Source KafkaConnector 리소스 |
| `mqttx-cli-linux-x64` | MQTT 테스트용 CLI 바이너리             |

Kafka 설정에서는 `my-kafka`라는 이름을 사용하며, Kafka controller replica는 3개로 구성됩니다.

```yaml
fullnameOverride: my-kafka

extraConfig: |
  num.partitions=5
  auto.create.topics.enable=true

controller:
  replicaCount: 3

listeners:
  client:
    protocol: PLAINTEXT
```

---

## 6.3 Kafka Connect

Kafka Connect는 MQTT Broker에서 메시지를 읽어 Kafka Topic으로 전달하는 역할을 합니다.

`kafka-connect.yaml`에서는 Strimzi `KafkaConnect` 리소스를 정의합니다.

주요 설정은 다음과 같습니다.

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaConnect

metadata:
  name: my-kafka-connect
  namespace: kafka
  annotations:
    strimzi.io/use-connector-resources: "true"

spec:
  replicas: 2
  image: jihwan77/kafka-connect-with-camel-mqtt:latest
  bootstrapServers: my-kafka.kafka:9092
```

Kafka Connect는 `my-kafka.kafka:9092`를 bootstrap server로 사용합니다.

---

## 6.4 MQTT Source Connector

`mqtt-connector.yaml`은 MQTT 메시지를 Kafka로 전달하는 Source Connector입니다.

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaConnector

metadata:
  name: mqtt-source
  namespace: kafka
  labels:
    strimzi.io/cluster: my-kafka-connect

spec:
  tasksMax: 1
  class: org.apache.camel.kafkaconnector.mqttsource.CamelMqttsourceSourceConnector
  config:
    topics: mqtt
    camel.kamelet.mqtt-source.topic: test
    camel.kamelet.mqtt-source.brokerUrl: tcp://emqx-listener.mqtt:1883
    camel.kamelet.mqtt-source.clientId: kafka-connector
    value.converter: org.apache.kafka.connect.converters.ByteArrayConverter
```

이 설정은 다음 의미를 가집니다.

| 항목              | 설명                              |
| --------------- | ------------------------------- |
| MQTT Broker     | `tcp://emqx-listener.mqtt:1883` |
| MQTT Topic      | `test`                          |
| Kafka Topic     | `mqtt`                          |
| Connector Class | Camel MQTT Source Connector     |
| Converter       | ByteArrayConverter              |

즉, EMQX의 `test` 토픽으로 들어온 MQTT 메시지를 Kafka의 `mqtt` 토픽으로 전달합니다.

---

## 6.5 Spring Boot Data Processing App

`spring boot/data_processing_app.java`는 Kafka 메시지를 소비하고 데이터를 처리하는 Spring Boot 애플리케이션입니다.

주요 기능은 다음과 같습니다.

```text
1. Kafka Topic `mqtt` 메시지 소비
2. 메시지 문자열 변환 및 가공
3. 처리된 메시지를 MQTT Topic `test1`로 재발행
4. 메시지를 메모리 버퍼에 적재
5. 일정 조건 도달 시 AWS S3에 배치 업로드
```

Kafka Consumer 설정:

```java
@KafkaListener(topics = "mqtt", groupId = "my-connect-consumer")
public void consumeAndPublish(ConsumerRecord<String, byte[]> record) {
    String input = new String(record.value());
    String processed = input.toUpperCase();
}
```

MQTT Broker 설정:

```java
private final String mqttBroker = "tcp://emqx-listener.mqtt:1883";
private final String mqttTopic = "test";
private final String mqttTopic2 = "test1";
```

S3 업로드는 버퍼링 방식으로 동작합니다.

```java
private static final int BATCH_SIZE = 100;
private static final long FLUSH_INTERVAL_MS = 60_000;
private static final int MAX_BUFFER_BYTES = 1024 * 1024;
```

처리된 데이터는 일정 개수 또는 크기 조건에 따라 S3에 업로드됩니다.

---

## 6.6 Spring Boot Kubernetes Deployment

`spring boot/replicant_app.yaml`은 데이터 처리 애플리케이션을 Kubernetes Deployment로 배포합니다.

주요 설정은 다음과 같습니다.

```yaml
apiVersion: apps/v1
kind: Deployment

metadata:
  name: data-processing-app
  namespace: processing

spec:
  replicas: 1
```

컨테이너 이미지는 다음을 사용합니다.

```yaml
image: jihwan77/data-kafka-app:3.0
```

AWS S3 접근을 위해 Kubernetes Secret에서 환경 변수를 주입합니다.

```yaml
env:
  - name: AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: aws-credentials
        key: AWS_ACCESS_KEY_ID

  - name: AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: aws-credentials
        key: AWS_SECRET_ACCESS_KEY

  - name: AWS_REGION
    valueFrom:
      secretKeyRef:
        name: aws-credentials
        key: AWS_REGION
```

---

## 6.7 Application Service

`spring boot/app_service.yaml`은 Spring Boot 애플리케이션을 NodePort로 노출합니다.

```yaml
apiVersion: v1
kind: Service

metadata:
  name: data-processing-service
  namespace: processing

spec:
  type: NodePort
  ports:
    - port: 8080
      targetPort: 8080
      nodePort: 30080
```

외부에서는 Kubernetes Node IP와 NodePort `30080`을 통해 접근할 수 있습니다.

```bash
curl http://<NODE_IP>:30080
```

---

## 6.8 HPA

`spring boot/app_hpa.yaml`은 데이터 처리 애플리케이션의 Horizontal Pod Autoscaler 설정입니다.

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler

metadata:
  name: data-processing-app-hpa
  namespace: processing

spec:
  minReplicas: 1
  maxReplicas: 4
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 30
```

CPU 사용률 평균 30%를 기준으로 Pod 수를 1개에서 최대 4개까지 자동 확장하도록 구성했습니다.

---

## 6.9 AWS S3 Secret

`aws-s3/aws-secret.yaml`은 Spring Boot 애플리케이션에서 AWS S3에 접근하기 위한 인증 정보를 Kubernetes Secret으로 관리하기 위한 파일입니다.

> 주의: 실제 운영 환경에서는 AWS Access Key와 Secret Key를 GitHub에 직접 커밋하지 않아야 합니다.
> 민감 정보는 Sealed Secret, External Secrets Operator, AWS IAM Role for Service Account, GitHub Secrets 등을 사용해 관리하는 것이 좋습니다.

---

## 6.10 HAProxy

`haproxy/` 디렉터리에는 HAProxy 배포를 위한 Kubernetes Manifest가 포함되어 있습니다.

| 파일                       | 설명                 |
| ------------------------ | ------------------ |
| `haproxy-configmap.yaml` | HAProxy 설정 파일 관리   |
| `haproxy-deploy.yaml`    | HAProxy Deployment |
| `haproxy-service.yaml`   | HAProxy Service    |

HAProxy는 Kubernetes 내부 서비스 앞단에서 트래픽을 분산하거나 특정 서비스 접근 경로를 구성하는 용도로 사용할 수 있습니다.

---

## 6.11 Calico

`calico-3.26.4/` 디렉터리에는 Kubernetes CNI로 사용할 Calico 관련 Manifest가 포함되어 있습니다.

| 파일                   | 설명                    |
| -------------------- | --------------------- |
| `calico.yaml`        | Calico 기본 설치 Manifest |
| `calico-custom.yaml` | Calico 커스텀 설정         |
| `calico/`            | Calico 관련 추가 리소스      |

Calico는 Pod 간 네트워크 통신과 NetworkPolicy 적용을 담당합니다.

---

## 6.12 mqtt-cluster Helm Chart

`mqtt-cluster/` 디렉터리는 MQTT 클러스터 구성을 Helm Chart 형태로 관리하기 위한 구조입니다.

```text
mqtt-cluster/
├── templates/
├── Chart.yaml
└── values.yaml
```

YAML을 직접 적용하는 방식 외에도 Helm Chart 기반으로 MQTT 클러스터를 템플릿화하여 배포할 수 있도록 구성한 것으로 볼 수 있습니다.

---

## 7. Namespace 구성 예시

프로젝트 구성상 다음 namespace를 사용합니다.

```bash
kubectl create namespace mqtt
kubectl create namespace kafka
kubectl create namespace processing
```

| Namespace    | 역할                                   |
| ------------ | ------------------------------------ |
| `mqtt`       | EMQX MQTT Broker                     |
| `kafka`      | Kafka, Kafka Connect, KafkaConnector |
| `processing` | Spring Boot 데이터 처리 애플리케이션            |

---

## 8. 배포 순서

아래 순서로 배포하는 것을 권장합니다.

```text
1. Namespace 생성
2. Calico CNI 적용
3. EMQX MQTT Broker 배포
4. Kafka 배포
5. Kafka Connect 배포
6. MQTT Source Connector 배포
7. AWS S3 Secret 생성
8. Spring Boot Data Processing App 배포
9. Service / HPA 적용
10. 메시지 송수신 테스트
```

---

## 9. 배포 방법

### 9.1 Namespace 생성

```bash
kubectl create namespace mqtt
kubectl create namespace kafka
kubectl create namespace processing
```

---

### 9.2 Calico 적용

```bash
kubectl apply -f calico-3.26.4/calico.yaml
kubectl apply -f calico-3.26.4/calico-custom.yaml
```

---

### 9.3 EMQX 배포

```bash
kubectl apply -f emqx/emqx-k8s-rbac.yaml
kubectl apply -f emqx/emqx-headless.yaml
kubectl apply -f emqx/emqx-core-k8s.yaml
kubectl apply -f emqx/emqx-replicant-k8s.yaml
kubectl apply -f emqx/emqx-listener.yaml
kubectl apply -f emqx/emqx-dashboard.yaml
kubectl apply -f emqx/emqx-replicant-hpa.yaml
```

배포 확인:

```bash
kubectl get pods -n mqtt -o wide
kubectl get svc -n mqtt
```

---

### 9.4 Kafka 배포

Kafka는 Helm values 파일을 사용합니다.

```bash
helm install my-kafka bitnami/kafka \
  -n kafka \
  -f kafka/kafka-values.yaml
```

배포 확인:

```bash
kubectl get pods -n kafka -o wide
kubectl get svc -n kafka
```

---

### 9.5 Kafka Connect 배포

```bash
kubectl apply -f kafka/kafka-connect.yaml
```

배포 확인:

```bash
kubectl get kafkaconnect -n kafka
kubectl get pods -n kafka
```

---

### 9.6 MQTT Source Connector 배포

```bash
kubectl apply -f kafka/mqtt-connector.yaml
```

Connector 상태 확인:

```bash
kubectl get kafkaconnector -n kafka
kubectl describe kafkaconnector mqtt-source -n kafka
```

---

### 9.7 AWS Secret 생성

`aws-s3/aws-secret.yaml` 파일을 적용합니다.

```bash
kubectl apply -f aws-s3/aws-secret.yaml
```

Secret 확인:

```bash
kubectl get secret -n processing
```

> 실제 운영 환경에서는 GitHub 저장소에 실제 AWS 인증 정보를 업로드하지 않도록 주의해야 합니다.

---

### 9.8 Spring Boot 애플리케이션 배포

```bash
kubectl apply -f "spring boot/replicant_app.yaml"
kubectl apply -f "spring boot/app_service.yaml"
kubectl apply -f "spring boot/app_hpa.yaml"
```

배포 확인:

```bash
kubectl get pods -n processing -o wide
kubectl get svc -n processing
kubectl get hpa -n processing
```

---

## 10. 테스트 방법

### 10.1 MQTT 메시지 발행

MQTT 테스트 클라이언트를 이용해 EMQX Broker의 `test` 토픽으로 메시지를 발행합니다.

```bash
mqttx pub -h <EMQX_SERVICE_IP> -p 1883 -t test -m "hello kubernetes"
```

또는 Kubernetes 내부에서 테스트 Pod를 생성하여 MQTT 메시지를 발행할 수 있습니다.

---

### 10.2 Kafka Topic 확인

Kafka에 `mqtt` 토픽이 생성되었는지 확인합니다.

```bash
kubectl exec -it -n kafka <kafka-pod-name> -- \
  kafka-topics.sh --bootstrap-server my-kafka.kafka:9092 --list
```

Kafka 메시지 확인:

```bash
kubectl exec -it -n kafka <kafka-pod-name> -- \
  kafka-console-consumer.sh \
  --bootstrap-server my-kafka.kafka:9092 \
  --topic mqtt \
  --from-beginning
```

---

### 10.3 Spring Boot 로그 확인

```bash
kubectl logs -n processing deploy/data-processing-app -f
```

정상 동작 시 다음 흐름의 로그를 확인할 수 있습니다.

```text
Kafka 메시지 수신
데이터 처리
MQTT 메시지 재발행
S3 배치 업로드
```

---

### 10.4 S3 업로드 확인

AWS S3 버킷에서 다음 경로 형태로 파일이 업로드되는지 확인합니다.

```text
s3://<bucket-name>/test/<timestamp>.txt
```

---

## 11. 주요 포트

| 구성 요소                |    포트 | 설명              |
| -------------------- | ----: | --------------- |
| EMQX MQTT            |  1883 | MQTT 메시지 송수신    |
| EMQX Dashboard       | 18083 | EMQX 관리 UI      |
| Kafka Client         |  9092 | Kafka Client 접속 |
| Spring Boot App      |  8080 | 애플리케이션 내부 포트    |
| Spring Boot NodePort | 30080 | 외부 접근 포트        |

---

## 12. 운영 및 확인 명령어

### 전체 Pod 확인

```bash
kubectl get pods -A -o wide
```

### EMQX 확인

```bash
kubectl get pods -n mqtt
kubectl get svc -n mqtt
```

### Kafka 확인

```bash
kubectl get pods -n kafka
kubectl get svc -n kafka
kubectl get kafkaconnector -n kafka
```

### Processing App 확인

```bash
kubectl get pods -n processing
kubectl get svc -n processing
kubectl get hpa -n processing
kubectl logs -n processing deploy/data-processing-app -f
```

---

## 13. 트러블슈팅

## 13.1 MQTT Connector가 메시지를 가져오지 못하는 경우

확인할 항목:

```bash
kubectl describe kafkaconnector mqtt-source -n kafka
kubectl logs -n kafka <kafka-connect-pod-name>
kubectl get svc -n mqtt
```

점검 포인트:

```text
- emqx-listener.mqtt 서비스가 존재하는지 확인
- MQTT Broker 포트 1883이 열려 있는지 확인
- MQTT Topic 이름이 test로 일치하는지 확인
- Kafka Topic mqtt가 생성되었는지 확인
```

---

## 13.2 Spring Boot 앱에서 S3 업로드가 실패하는 경우

확인할 항목:

```bash
kubectl logs -n processing deploy/data-processing-app -f
kubectl get secret -n processing
kubectl describe secret aws-credentials -n processing
```

점검 포인트:

```text
- AWS_ACCESS_KEY_ID 존재 여부
- AWS_SECRET_ACCESS_KEY 존재 여부
- AWS_REGION 존재 여부
- aws.bucket.name 설정 여부
- S3 Bucket 권한 확인
```

---

## 13.3 Kafka Connect Pod가 실행되지 않는 경우

확인할 항목:

```bash
kubectl get pods -n kafka
kubectl describe pod -n kafka <kafka-connect-pod-name>
kubectl logs -n kafka <kafka-connect-pod-name>
```

점검 포인트:

```text
- KafkaConnect 이미지가 정상 pull 되는지 확인
- jihwan77/kafka-connect-with-camel-mqtt:latest 이미지에 MQTT Source Connector 플러그인이 포함되어 있는지 확인
- bootstrapServers 주소가 my-kafka.kafka:9092와 일치하는지 확인
```

---

## 13.4 EMQX 클러스터가 정상 구성되지 않는 경우

확인할 항목:

```bash
kubectl get pods -n mqtt -o wide
kubectl logs -n mqtt <emqx-pod-name>
kubectl get svc -n mqtt
```

점검 포인트:

```text
- emqx-headless 서비스 존재 여부
- EMQX_CLUSTER__K8S__SERVICE_NAME 값 확인
- EMQX_CLUSTER__K8S__NAMESPACE 값 확인
- emqx-k8s-rbac.yaml 적용 여부
```

---

## 13.5 HPA가 동작하지 않는 경우

확인할 항목:

```bash
kubectl get hpa -n processing
kubectl top pods -n processing
```

점검 포인트:

```text
- Metrics Server 설치 여부
- Deployment에 resources.requests.cpu 설정 여부
- HPA target CPU averageUtilization 설정 확인
```



---

## 14. 개선 방향

* CI/CD 파이프라인 추가
* Dockerfile 및 Spring Boot 빌드 과정 정리
* Kafka Connect 이미지 빌드 과정 문서화
* EMQX 인증 기능 추가
* Kafka SASL/TLS 적용
* AWS S3 업로드 실패 재시도 로직 개선
* Prometheus/Grafana 기반 모니터링 강화
* Helm Chart 기반 배포 방식 통합
* Kustomize 또는 Helm으로 환경별 설정 분리
* NetworkPolicy 기반 namespace 간 통신 제어
* Argo CD를 이용한 GitOps 배포 구조 확장

---

## 15. 프로젝트를 통해 학습한 내용

* Kubernetes Manifest 기반 서비스 배포
* EMQX MQTT Broker 클러스터 구성
* Kafka 및 Kafka Connect 연동 구조
* MQTT Source Connector를 이용한 MQTT → Kafka 데이터 전달
* Spring Boot Kafka Consumer 구현
* MQTT 재발행 구조 구현
* AWS S3 SDK를 이용한 배치 업로드
* Kubernetes Secret 기반 환경 변수 주입
* HPA를 이용한 Pod Auto Scaling 구성
* Kubernetes namespace 단위 서비스 분리

---


