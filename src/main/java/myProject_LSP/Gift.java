package myProject_LSP;

import org.springframework.beans.BeanUtils;

import javax.persistence.*;

@Entity
@Table(name="Gift_table")
public class Gift {

    //수정
    private static int giftQty=10;

    private boolean giftFlowChk=true;

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private String status;
    private Long sendDate;
    private String giftKind;

    @PostPersist
    public void onPostPersist(){
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ 26 ");
        System.out.println(this.getStatus());
        if("GIFT : GIFT SENDED".equals(this.getStatus())){
            //ORDER -> GIFT SEND 경우
            GiftSended giftSended = new GiftSended();
            BeanUtils.copyProperties(this, giftSended);
            giftSended.publishAfterCommit();
        }
    }

    @PrePersist
    public void onPrePersist(){

        System.out.println(this.getStatus() + "++++++++++++++++++++++++++++++++");

        if("ORDER : GIFT SEND".equals(this.getStatus())){
            this.setGiftKind("Candy");
            this.setStatus("GIFT : GIFT SENDED");
            this.setSendDate(System.currentTimeMillis());
        }else {
            this.setStatus("GIFT : GIFT SEND CANCELLED");
            //ORDER -> GIFT CANCEL 경우

            System.out.println("47 *****************************************");
            GiftSendCancelled giftSendCancelled = new GiftSendCancelled();
            BeanUtils.copyProperties(this, giftSendCancelled);
            giftSendCancelled.publishAfterCommit();
        }
    }


    @PreUpdate
    public void onPreUpdate(){
        if("ORDER : GIFT SEND".equals(this.getStatus())){
            this.setGiftKind("Candy");
            this.setStatus("GIFT : GIFT SENDED");
            this.setSendDate(System.currentTimeMillis());
        }else {
            this.setStatus("GIFT : GIFT SEND CANCELLED");
            //ORDER -> GIFT CANCEL 경우

            GiftSendCancelled giftSendCancelled = new GiftSendCancelled();
            BeanUtils.copyProperties(this, giftSendCancelled);
            giftSendCancelled.publishAfterCommit();
        }


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public Long getSendDate() {
        return sendDate;
    }

    public void setSendDate(Long sendDate) {
        this.sendDate = sendDate;
    }
    public String getGiftKind() {
        return giftKind;
    }

    public void setGiftKind(String giftKind) {
        this.giftKind = giftKind;
    }




}
