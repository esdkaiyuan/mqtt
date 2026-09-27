package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.ProductRequest;
import com.mqtt.cloud.entity.Product;

import java.util.List;

public interface ProductService {

    Product create(ProductRequest request);

    List<Product> list();

    Product update(Long id, ProductRequest request);

    void delete(Long id);

    Product getByProductKey(String productKey);

    Product requireById(Long id);
}