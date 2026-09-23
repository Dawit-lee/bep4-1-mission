package com.back.boundedContext.market.out;

import com.back.boundedContext.market.domain.Order;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface OrderRepository extends CrudRepository<Order, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForPayment(@Param("id") int id);
}
