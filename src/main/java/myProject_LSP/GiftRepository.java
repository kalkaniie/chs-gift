package myProject_LSP;

import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;
import java.util.Optional;

public interface GiftRepository extends PagingAndSortingRepository<Gift, Long>{
        Optional<Gift> findByOrderId(Long orderId);

}