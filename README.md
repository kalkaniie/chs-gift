# Intensive Lv2. TeamC-사은품(Gift)

음식을 주문하고 요리하여 배달하고 사은품이 지급되는 현황을 확인 할 수 있는 CNA의 개발

# Table of contents

- [Restaurant](# )
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현:](#구현)
    - [DDD 의 적용](#ddd-의-적용)
    - [동기식 호출 과 Fallback 처리](#동기식-호출과-Fallback-처리)
    - [비동기식 호출과 Saga Pattern](#비동기식-호출과-Saga-Pattern)
    - [Gateway](#Gateway)
    - [CQRS](#CQRS)
  - [운영](#운영)
    - [AWS를 활용한 코드 자동빌드 배포 환경구축](#AWS를-활용한-코드-자동빌드-배포-환경구축)
    - [서킷 브레이킹과 오토스케일](서킷-브레이킹과-오토스케일)
    - [무정지 배포](#무정지-배포)
    - [마이크로서비스 로깅 관리를 위한 PVC 설정](#마이크로서비스-로깅-관리를-위한-PVC-설정)
    - [SelfHealing](#SelfHealing)
  - [첨부](#첨부)

# 서비스 시나리오

음식을 주문하고, 요리현황, 배달, 사은품지급 현황을 조회

## 기능적 요구사항

1. 고객이 주문을 하면 주문정보를 바탕으로 요리가 시작된다.
1. 고객이 주문을 하면 신규 사은품이 지급된다.
1. 요리가 완료되면 배달이 시작된다.
1. 고객이 주문취소를 하게 되면 요리가 취소된다.
1. 고객이 주문취소를 하게 되면 사은품 지급이 취소된다.
1. 고객 주문에 재고가 없을 경우 주문이 취소된다. 
1. 고객은 Mypage를 통해, 주문과 요리, 배달, 사은품지급의 전체 상황을 조회할수 있다.

## 비기능적 요구사항
1. 장애격리
    1. 주문시스템이 과중되면 사용자를 잠시동안 받지 않고 잠시후에 주문하도록 유도한다.
    1. 주문 시스템이 죽을 경우 재기동 될 수 있도록 한다.
1. 운영
    1. 마이크로서비스별로 로그를 한 곳에 모아 볼 수 있도록 시스템을 구성한다.
    1. 마이크로서비스의 개발 및 빌드 배포가 한번에 이루어질 수 있도록 시스템을 구성한다.
    1. 서비스라우팅을 통해 한개의 접속점으로 서비스를 이용할 수 있도록 한다.
    1. 주문 시스템이 과중되면 Replica를 추가로 띄울 수 있도록 한다.

# 분석/설계

## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과 : http://www.msaez.io/#/storming/ckYIP4C7M2X8xqx0UvgE7lWE4Yq1/mine/eb4fba62e6af9c7f9069e28ac9b3f83b/-MHBHte6QeP1F2SNGp2w
![EventStorming_modeling](https://user-images.githubusercontent.com/68719410/93419662-5a576700-f8e8-11ea-9c30-2a937479ed65.png)

### 이벤트 도출
1. 주문됨
1. 주문취소됨
1. 요리재고체크됨
1. 요리완료
1. 배달
1. 사은품지급됨
1. 사은품지급취소됨 


### 어그리게잇으로 묶기

  * 고객의 주문(Order), 식당의 요리(Cook), 배달(Delivery), 사은품(Gift) 은 그와 연결된 command와 event 들에 의하여 트랙잭션이 유지되어야 하는 단위로 묶어 줌.

### Policy 부착 

### Policy와 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Res)

### 기능적 요구사항 검증
 * 고객이 메뉴를 주문한다.(ok)
 * 주문된 주문정보를 레스토랑으로 전달한다.(ok)
 * 주문정보를 바탕으로 요리가 시작된다.(ok)
 * 요리가 완료되면 배달이 시작된다.(ok)
 * 고객은 본인의 주문을 취소할 수 있다.(ok)
 * 주문이 취소되면 요리를 취소한다.(ok)
 * 주문이 취소되면, 요리취소 내용을 고객에게 전달한다.(ok)
 * 고객이 주문 시 재고량을 체크한다.(ok)
 * 재고가 없을 경우 주문이 취소된다.(ok)
 * 주문이 되면 사은품이 지급된다. (ok)
 * 주문이 취소되면 사은품지급이 취소한다. (ok)
 * 고객은 Mypage를 통해, 주문과 요리, 배달, 사은품지급의 전체 상황을 조회할수 있다.(ok)

</br>
</br>



# 구현:

분석/설계 단계에서 도출된 아키텍처에 따라, 각 BC별로 마이크로서비스들을 스프링부트 + JAVA로 구현하였다. 각 마이크로서비스들은 Kafka와 RestApi로 연동되며 스프링부트의 내부 H2 DB를 사용한다.


## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 주문- Order 마이크로서비스).

```
package myProject_LSP;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Gift_table")
public class Gift {

    private static int giftQty=10;
    private boolean giftFlowChk=true;

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private String status;
    private Long sendDate;
    private String giftKind;
    
    ....
}
```
- JPA를 활용한 Repository Pattern을 적용하여 이후 데이터소스 유형이 변경되어도 별도의 처리 없이 사용 가능한 Spring Data REST 의 RestRepository 를 적용하였다
```
package myProject_LSP;
import org.springframework.data.repository.PagingAndSortingRepository;
public interface GiftRepository extends PagingAndSortingRepository<Gift, Long>{

}
```
</br>

## 동기식 호출

분석단계에서의 조건 중 하나로 주문->취소 간의 호출은 트랜잭션으로 처리. 호출 프로토콜은 Rest Repository의 REST 서비스를 FeignClient 를 이용하여 호출.
- 사은품(gift) 서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```
@FeignClient(name="gift", url="${api.url.gift}")
public interface GiftService {

    @RequestMapping(method= RequestMethod.POST, path="/gifts")
    public void giftSend(@RequestBody Gift gift);

}
```

- 주문이 접수 될 경우 사은품지급 현황에 신규지급 내역을 접수한다.
```
   @PostPersist
    public void onPostPersist(){
        if("GIFT : GIFT SENDED".equals(this.getStatus())){
            //ORDER -> GIFT SEND 경우
            GiftSended giftSended = new GiftSended();
            BeanUtils.copyProperties(this, giftSended);
            giftSended.publishAfterCommit();
        }
        
```        
- GIFT 서비스에서 정상적으로 사은품이 지급되고 STATUS도 'GIFT SENDED'로 접수된다.
![gift발행](https://user-images.githubusercontent.com/68719410/93406695-43eee280-f8cb-11ea-8fca-386636d15c20.png)


</br>

## 비동기식 호출과 Saga Pattern

신규 사은품지급 및 신규 사은품지급 취소는 비동기식으로 처리하여 시스템 상황에 따라 접수 및 취소가 블로킹 되지 않도록 처리 한다. 
saga pattern : 
1. 고객이 음식 주문 시 Gift(사은품)으로 전달되어 신규 사은품이 지급된다.
1. 사은품지급이 완료 되면 Gift서비스에서 Order서비스에 Gift 상태를 전송한다. 
1. Order서비스는 Gift 상태를 업데이트한다. 
 
```

# 고객이 음식 주문 시, Gift에 신규지급 호출 (REST API)
 @PostPersist
    public void onPostPersist(){
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        if(!"ORDER : COOK CANCELED".equals(ordered.getStatus())){
            ordered.publishAfterCommit();
            /*수정*/
            Gift gift = new Gift();
            gift.setOrderId(this.getId());
            gift.setStatus("ORDER : GIFT SEND");
            OrderApplication.applicationContext.getBean(myProject_LSP.external.GiftService.class).giftSend(gift);
        }
```
- 주문 정상 접수

![주문정상접수](https://user-images.githubusercontent.com/68719410/93408550-d2fdf980-f8cf-11ea-83f3-9bea034a70c4.png)

![gift발행](https://user-images.githubusercontent.com/68719410/93406695-43eee280-f8cb-11ea-8fca-386636d15c20.png)

```
# 사은품지급이 완료 되면 Gift서비스에서 Order서비스에 Gift 상태를 전송한다. (PUB/SUB)
            
     @PostPersist
    public void onPostPersist(){
        if("GIFT : GIFT SENDED".equals(this.getStatus())){
            //ORDER -> GIFT SEND 경우
            GiftSended giftSended = new GiftSended();
            BeanUtils.copyProperties(this, giftSended);
            giftSended.publishAfterCommit();
            
    @PrePersist
    public void onPrePersist(){
        if("ORDER : GIFT SEND".equals(this.getStatus())){
            this.setGiftKind("Candy");
            this.setStatus("GIFT : GIFT SENDED");
            this.setSendDate(System.currentTimeMillis());
        }

```          
```            
 # 주문정보 상태가 업데이트 된다.
     @PreUpdate
    public void onPreUpdate(){
        if("ORDER : GIFT SEND".equals(this.getStatus())){
            this.setGiftKind("Candy");
            this.setStatus("GIFT : GIFT SENDED");
            this.setSendDate(System.currentTimeMillis());
            
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverGiftSended_GiftInfoUpdate(@Payload GiftSended giftSended){

        if(giftSended.isMe()){
            System.out.println("##### listener GiftInfoUpdate : " + giftSended.toJson());
            Optional<Order> orderOptional = orderRepository.findById(giftSended.getOrderId());
            Order order = orderOptional.get();
            if("GIFT : GIFT SENDED".equals(giftSended.getStatus())){
                order.setGiftStatus("ORDER : GIFT SENDED SUCCESS");
            }
            orderRepository.save(order);      
```
- ORDER 서비스에서 GIFT 상태 업데이트 확인 (SAGA 패턴 확인)
![giftsendsuccess](https://user-images.githubusercontent.com/68719410/93407599-8a454100-f8cd-11ea-91b0-45bc5fb86280.png)
</br>

## Gateway
하나의 접점으로 서비스를 관리할 수 있는 Gateway를 통한 서비스라우팅을 적용 한다. Loadbalancer를 이용한 각 서비스의 접근을 확인 함.

```
# Gateway 설정(https://github.com/dew0327/final-cna-gateway/blob/master/target/classes/application.yml)
spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: order
          uri: http://order:8080
          predicates:
            - Path=/orders/**
        - id: cook
          uri: http://cook:8080
          predicates:
            - Path=/cooks/**,/cancellations/**
        - id: delivery
          uri: http://delivery:8080
          predicates:
            - Path=/deliveries/**
        - id: mypage
          uri: http://mypage:8080
          predicates:
            - Path= /mypages/**
        - id: gift
          uri: http://gift:8080
          predicates:
            - Path= /gifts/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080
```
![gateway_loadbalancer](https://user-images.githubusercontent.com/68719410/93343128-18d4a680-f86b-11ea-8291-b480e3deaaee.png)

![gift수행](https://user-images.githubusercontent.com/68719410/93343219-34d84800-f86b-11ea-858c-9572bbdf730b.png)

</br>

## CQRS
기존 코드에 영향도 없이 mypage 용 materialized view 구성한다. 고객은 주문 접수, 요리 상태, 배송현황, 사은품 지급현황 등을 한개의 페이지에서 확인 할 수 있게 됨.</br>
```
# 사은품 내역 mypage에 insert
    @StreamListener(KafkaProcessor.INPUT)
    public void whenGiftSended_then_UPDATE_6(@Payload GiftSended giftSended) {
        try {
            if (giftSended.isMe()) {
                // view 객체 조회
                
                List<Mypage> mypageList = mypageRepository.findByOrderId(giftSended.getOrderId());
                for(Mypage mypage : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setGiftId(giftSended.getId());
                    mypage.setGiftStatus(giftSended.getStatus());
                    mypage.setGiftSendDate(giftSended.getSendDate());
                    mypage.setGiftKind(giftSended.getGiftKind());


                    // view 레파지 토리에 save
                    mypageRepository.save(mypage);
                    
         }
# 사은품지급(GiftSend) mypage 업데이트         
    @StreamListener(KafkaProcessor.INPUT)
    public void whenGiftSendCancelled_then_UPDATE_7(@Payload GiftSendCancelled giftSendCancelled) {
        try {
            if (giftSendCancelled.isMe()) {
                System.out.println("***************************************");
                // view 객체 조회
                List<Mypage> mypageList = mypageRepository.findByOrderId(giftSendCancelled.getOrderId());
                for(Mypage mypage : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setGiftId(giftSendCancelled.getId());
                    mypage.setGiftStatus(giftSendCancelled.getStatus());
                    mypage.setGiftSendDate(giftSendCancelled.getSendDate());
                    mypage.setGiftKind(giftSendCancelled.getGiftKind());
                    // view 레파지 토리에 save
                    mypageRepository.save(mypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }               
 ```
![CQRS](https://user-images.githubusercontent.com/68719410/93343798-e5dee280-f86b-11ea-944d-ee6610254eee.png)

 ```
 # 사은품지급 취소(GiftSendCancel) mypage 업데이트
    @Autowired
    GiftRepository giftRepository;
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrderCancelled_GiftSendCancel(@Payload OrderCancelled orderCancelled){

        //수정
        if(orderCancelled.isMe()){

            System.out.println("##### listener GiftCancelUpdate : " + orderCancelled.toJson());
            Optional<Gift> giftOptional = giftRepository.findByOrderId(orderCancelled.getId());
            Gift gift = giftOptional.get();
            if("ORDER : ORDER CANCELED".equals(orderCancelled.getStatus())){
                gift.setStatus("GIFT : GIFT SEND CANCELLED BY ORDER CANCEL");

            }
           giftRepository.save(gift);

        }
 ```
 - mypage에 GIFT 삭제상태 업데이트 확인
 
![mypage_ordercancel](https://user-images.githubusercontent.com/68719410/93408239-0429fa00-f8cf-11ea-9535-72e394f763f1.png)

![mypage_giftcancel](https://user-images.githubusercontent.com/68719410/93408277-23c12280-f8cf-11ea-8ceb-67535a952f75.png)
</br>
</br>


# 운영

## AWS를 활용한 코드 자동빌드 배포 환경구축

  * AWS codebuild를 설정하여 github이 업데이트 되면 자동으로 빌드 및 배포 작업이 이루어짐.
  * Github에 Codebuild를 위한 yml 파일을 업로드하고, codebuild와 연동 함
  * 각 마이크로서비스의 build 스펙
  ```
    https://github.com/kalkaniie/chs-order/blob/master/buildspec.yml
    https://github.com/kalkaniie/chs-cook/blob/master/buildspec.yml
    https://github.com/kalkaniie/chs-delivery/blob/master/buildspec.yml
    https://github.com/kalkaniie/chs-gateway/blob/master/buildspec.yml
    https://github.com/kalkaniie/chs-mypage/blob/master/buildspec.yml
    https://github.com/kalkaniie/chs-gift/blob/master/buildspec.yml
  ```
  
</br>

## 서킷 브레이킹과 오토스케일

* 서킷 브레이킹 :
주문이 과도할 경우 CB 를 통하여 장애격리. 500 에러가 5번 발생하면 10분간 CB 처리하여 100% 접속 차단
```
# AWS codebuild에 설정(https://github.com/kalkaniie/chs-gift/blob/master/buildspec.yml)
 http:
   http1MaxPendingRequests: 1   # 연결을 기다리는 request 수를 1개로 제한 (Default 
   maxRequestsPerConnection: 1  # keep alive 기능 disable
 outlierDetection:
  consecutiveErrors: 1          # 5xx 에러가 5번 발생하면
  interval: 1s                  # 1초마다 스캔 하여
  baseEjectionTime: 10m         # 10분 동안 circuit breaking 처리   
  maxEjectionPercent: 100       # 100% 로 차단
```

* 오토스케일(HPA) :
CPU사용률 10% 초과 시 replica를 5개까지 확장해준다. 상용에서는 70%로 세팅하지만 여기에서는 기능적용 확인을 위해 수치를 조절.
```
apiVersion: autoscaling/v1
kind: HorizontalPodAutoscaler
metadata:
  name: skcchpa-gift
  namespace: teamc
  spec:
    scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: $IMAGE_REPO_NAME              # gift (사은품) 서비스 HPA 설정
    minReplicas: 3                      # 최소 3개
    maxReplicas: 5                      # 최대 5개
    targetCPUUtilizationPercentage: 10  # cpu사용율 10프로 초과 시 
```    
* 부하테스트(Siege)를 활용한 부하 적용 후 서킷브레이킹 / 오토스케일 내역을 확인한다.
1. 부하테스트 전, minReplicas=1로 1개의 pod만이 떠있는 것을 확인
![gift_pod_1개](https://user-images.githubusercontent.com/68719410/93409474-f4f87b80-f8d1-11ea-81de-1d299e648e9e.png)

1. 부하테스트하여, 오토스케일링 된 pod 내역 확인
```  
HTTP/1.1 503     0.08 secs:       19 bytes ==> GET  /gifts
HTTP/1.1 503     0.07 secs:       19 bytes ==> GET  /gifts
HTTP/1.1 503     0.07 secs:       19 bytes ==> GET  /gifts
HTTP/1.1 503     0.07 secs:       19 bytes ==> GET  /gifts
HTTP/1.1 503     0.08 secs:       19 bytes ==> GET  /gifts
HTTP/1.1 503     0.09 secs:       19 bytes ==> GET  /gifts
HTTP/1.1 503     0.08 secs:       19 bytes ==> GET  /gifts
HTTP/1.1 503     0.08 secs:       19 bytes ==> GET  /gifts
HTTP/1.1 503     0.07 secs:       19 bytes ==> GET  /gifts
siege aborted due to excessive socket failure; you
can change the failure threshold in $HOME/ .siegerc

Transactions:                  2505 hits
Availability:                    70.40 %
Elapsed time:                 23.86 secs
Data transferred:               1.88 MB
Response time:                 0.53 secs
Transaction rate:             104.99 trans/sec
Throughput:                     0.08 MB/sec
Concurrency:                  61.38
Successful transactions:       2505
```  
![gifts_circuitbreaker](https://user-images.githubusercontent.com/68719410/93409285-85828c00-f8d1-11ea-98d0-ddc9de82154d.png)

![gift_pod_3개늘어남](https://user-images.githubusercontent.com/68719410/93409351-acd95900-f8d1-11ea-819d-b6fa0fe86f38.png)

![gift_replica_3개_2](https://user-images.githubusercontent.com/68719410/93409431-da260700-f8d1-11ea-8a78-4afc5b135990.png)

</br>

## 무정지 배포

* 무정지 배포를 위해 ECR 이미지를 업데이트 하고 이미지 체인지를 시도 함. Github에 소스가 업데이트 되면 자동으로 AWS CodeBuild에서 컴파일 하여 이미지를 ECR에 올리고 EKS에 반영.
  이후 아래 옵션에 따라 무정지 배포 적용 된다.
  

```
# AWS codebuild에 설정(https://github.com/kalkaniie/chs-gift/blob/master/buildspec.yml)
  spec:
    replicas: 2
    minReadySeconds: 10   # 최소 대기 시간 10초
    strategy:
      type: RollingUpdate
      rollingUpdate:
      maxSurge: 1         # 1개씩 업데이트 진행
      maxUnavailable: 0   # 업데이트 프로세스 중에 사용할 수 없는 최대 파드의 수

```

- 새버전으로의 배포 시작(V1로 배포)
![gift_v1_image](https://user-images.githubusercontent.com/68719410/93348947-d498d480-f871-11ea-97d3-8e7a09cb3759.png)

- 새버전 배포 확인(V1 적용)
![무중단running_v1](https://user-images.githubusercontent.com/68719410/93349106-090c9080-f872-11ea-906c-fc858d891f04.png)


- Readiness Probe 설정을 통한 ZeroDownTime 설정.
```
  readinessProbe:
    tcpSocket:
      port: 8080
      initialDelaySeconds: 180      # 서비스 어플 기동 후 180초 뒤 시작
      periodSeconds: 120            # 120초 주기로 readinessProbe 실행 
```
![ZeroDownTime  SEIGE_STATUS_read](https://user-images.githubusercontent.com/54210936/93278989-1473a380-f801-11ea-8140-f7edbc2c9b6f.jpg)


</br>

## 마이크로서비스 로깅 관리를 위한 PVC 설정
AWS의 EFS에 파일시스템을 생성(EFS-teamc (fs-29564908))하고 서브넷과 클러스터(admin16-cluster)를 연결하고 PVC를 설정해준다. 각 마이크로 서비스의 로그파일이 EFS에 정상적으로 생성되고 기록됨을 확인 함.
```
#AWS의 각 codebuild에 설정(https://github.com/kalkaniie/chs-gift/blob/master/buildspec.yml)
volumeMounts:  
- mountPath: "/mnt/aws"    # GIFT서비스 로그파일 생성 경로
  name: volume                 
volumes:                                # 로그 파일 생성을 위한 EFS, PVC 설정 정보
- name: volume
  persistentVolumeClaim:
  claimName: aws-efs  
```
![PVC](https://user-images.githubusercontent.com/68719410/93351910-3b6bbd00-f875-11ea-802f-89fae3b43983.png)

</br>

## SelfHealing
운영 안정성의 확보를 위해 마이크로서비스가 아웃된 뒤에 다시 프로세스가 올라오는 환경을 구축한다. 프로세스가 죽었을 때 다시 기동됨을 확인함.
```
#AWS의 각 codebuild에 설정(https://github.com/kalkaniie/chs-cook/blob/master/buildspec.yml)
livenessProbe:
  exec:
   command:
   - cat
   - /mnt/aws/logs/cook-application.log
  initialDelaySeconds: 20  # 서비스 어플 기동 후 20초 뒤 시작
  periodSeconds: 30  # 30초 주기로 livenesProbe 실행
```
- /mnt/aws/logs/cook-application.log 가 삭제 될 때, cook 프로세스가 kill된 후에, 자동으로 SelfHealing되는 과정 확인

![self healing_2](https://user-images.githubusercontent.com/68719410/93357433-4295c980-f87b-11ea-98b6-6f3f37a25466.png)
![self healing_3](https://user-images.githubusercontent.com/68719410/93357510-57725d00-f87b-11ea-8feb-40e174494c5f.png)
![self healing_2](https://user-images.githubusercontent.com/68719410/93357602-74a72b80-f87b-11ea-8eea-be01ccb0cd8f.png)

</br>
</br>



# 첨부
팀프로젝트 구성을 위해 사용한 계정 정보 및 클러스터 명, Github 주소 등의 내용 공유 
* AWS 계정 명 : TeamC
```
Region : ap-northeast-1
EFS : EFS-teamc (fs-29564908)
EKS : TeamC-final
ECR : order / delivery / cook / mypage / gateway
Codebuild : order / delivery / cook / mypage / gateway
```
* Github :</br>
```
https://github.com/dew0327/final-cna-gateway
https://github.com/dew0327/final-cna-order
https://github.com/dew0327/final-cna-delivery
https://github.com/dew0327/final-cna-cook
https://github.com/dew0327/final-cna-mypage
```
