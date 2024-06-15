package org.home.repository;

import org.home.dao.Info;
import org.home.dao.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InfoRepository extends JpaRepository<Info, Integer> {
}
