package com.mqtt.cloud.service;

import com.mqtt.cloud.dto.request.ProductRequest;
import com.mqtt.cloud.entity.Product;

import java.util.List;

public interface ProductService {

    Product create(ProductRequest request);

    List<Product> list();

    Product update(Long id, ProductRequest request);

    void delete(Long id);

    /**
     * 停用产品：旗下所有设备将无法通过认证，并主动失效该产品下全部设备的认证缓存。
     * 已停用时重复调用幂等成功。
     */
    void disableProduct(Long id);

    /**
     * 启用产品：恢复旗下设备的认证许可，并主动失效该产品下全部设备的认证缓存。
     * 已启用时重复调用幂等成功。
     */
    void enableProduct(Long id);

    Product getByProductKey(String productKey);

    Product requireById(Long id);
}