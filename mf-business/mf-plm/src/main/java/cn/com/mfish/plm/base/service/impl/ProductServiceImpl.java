package cn.com.mfish.plm.base.service.impl;

import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.plm.base.bean.container.Product;
import cn.com.mfish.plm.base.mapper.container.ProductMapper;
import cn.com.mfish.plm.base.service.ProductService;
import cn.com.mfish.plm.producer.PlmGraphSyncProducer;
import cn.com.mfish.plm.utils.GraphUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Collections;

/**
 * 产品服务实现
 *
 * @author mfish
 * @date 2026-04-17
 */
@Slf4j
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired(required = false)
    private PlmGraphSyncProducer graphSyncProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Product product) {
        boolean result = super.save(product);
        if (result && graphSyncProducer != null) {
            try {
                GraphSyncEvent event = new GraphSyncEvent();
                event.setEventType("CREATE");
                event.setOperator(product.getCreateBy());
                event.setNodes(Collections.singletonList(GraphUtils.toGraphNode(product)));
                graphSyncProducer.sendGraphSyncEvent(event);
            } catch (Exception e) {
                log.error("发送产品创建图同步事件失败, productId={}", product.getId(), e);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Product product) {
        boolean result = super.updateById(product);
        if (result && graphSyncProducer != null) {
            try {
                GraphSyncEvent event = new GraphSyncEvent();
                event.setEventType("UPDATE");
                event.setOperator(product.getUpdateBy());
                event.setNodes(Collections.singletonList(GraphUtils.toGraphNode(product)));
                graphSyncProducer.sendGraphSyncEvent(event);
            } catch (Exception e) {
                log.error("发送产品更新图同步事件失败, productId={}", product.getId(), e);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        Product product = super.getById(id);
        boolean result = super.removeById(id);
        if (result && graphSyncProducer != null && product != null) {
            try {
                GraphSyncEvent event = new GraphSyncEvent();
                event.setEventType("DELETE");
                event.setOperator(product.getUpdateBy());
                event.setNodes(Collections.singletonList(GraphUtils.toGraphNode(product)));
                graphSyncProducer.sendGraphSyncEvent(event);
            } catch (Exception e) {
                log.error("发送产品删除图同步事件失败, productId={}", id, e);
            }
        }
        return result;
    }
}