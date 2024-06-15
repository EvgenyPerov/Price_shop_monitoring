package org.home.dao;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column
    private String name;

    @Column
    private String url;

    @Column(columnDefinition = "TEXT")
    private  String tag;

    @Column
    private LocalDate lastUpdateDate;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<Info> infoList;
}
