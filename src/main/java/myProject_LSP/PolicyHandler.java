package myProject_LSP;


import myProject_LSP.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }
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
    }

}
