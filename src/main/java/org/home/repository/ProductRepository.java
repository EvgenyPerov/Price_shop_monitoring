package org.home.repository;

import org.home.dto.ResponseDto;
import org.home.dao.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    Optional<Product> findByNameAndUrl(String name, String url);

    @Query("""
                select new org.home.dto.ResponseDto(p.id, p.name, i.date, i.price) from Product AS p
                left join Info AS i on p.id = i.product.id ORDER BY 1,3
            """)
    List<ResponseDto> getProductInfo();

}
