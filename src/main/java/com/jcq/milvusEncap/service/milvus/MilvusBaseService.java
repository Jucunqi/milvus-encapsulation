package com.jcq.milvusEncap.service.milvus;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.jcq.milvusEncap.annotation.CollectionName;
import com.jcq.milvusEncap.dal.pojo.PageParam;
import com.jcq.milvusEncap.dal.pojo.PageResult;
import com.jcq.milvusEncap.exception.ServiceException;
import com.jcq.milvusEncap.strategy.CamelToUnderlineNamingStrategy;
import com.jcq.milvusEncap.util.CollectionUtils;
import com.jcq.milvusEncap.util.MilvusUtil;
import io.milvus.pool.MilvusClientV2Pool;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 封装Milvus CRUD base方法
 *
 * @author : jucunqi
 * @since : 2025/10/14
 */
@Slf4j
@Component
public abstract class MilvusBaseService<T> {

    @Value("${milvus.client-key}")
    private String clientKey;
    @Resource
    private MilvusClientV2Pool pool;

    // 存储当前子类的泛型T的实际Class（每个子类各自独立）
    private final Class<T> entityClass;

    @SuppressWarnings("unchecked")
    protected MilvusBaseService() { // 访问权限为protected，仅允许子类调用
        // 1. 获取当前子类的原始类型（绕过Spring代理）
        Class<?> currentClass = ClassUtils.getUserClass(this.getClass());

        // 2. 循环解析，直到找到泛型父类（MilvusBaseService）
        Type genericSuperclass;
        while (true) {
            genericSuperclass = currentClass.getGenericSuperclass();
            // 如果父类是MilvusBaseService的ParameterizedType，则退出循环
            if (genericSuperclass instanceof ParameterizedType
                    && ((ParameterizedType) genericSuperclass).getRawType().equals(MilvusBaseService.class)) {
                break;
            }
            // 否则继续向上查找父类（处理多层继承场景）
            currentClass = currentClass.getSuperclass();
            if (currentClass == Object.class) {
                throw new IllegalArgumentException("子类未正确继承MilvusBaseService<T>，请检查泛型定义");
            }
        }

        // 3. 提取泛型参数（T的实际类型）
        ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

        // 4. 校验并赋值（确保泛型是具体的Class类型）
        if (actualTypeArguments.length == 0 || !(actualTypeArguments[0] instanceof Class)) {
            throw new IllegalArgumentException(
                    "子类泛型参数无效，请指定具体实体类（如：public class UserService extends MilvusBaseService<User>）"
            );
        }
        this.entityClass = (Class<T>) actualTypeArguments[0];

        // 5. 校验子类泛型的注解（每个子类各自校验）
        validateCollectionNameAnnotation();
    }

    /**
     * 核心方法：获取T的CollectionName注解值
     */
    public String validateCollectionNameAnnotation() {
        CollectionName annotation = entityClass.getAnnotation(CollectionName.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    String.format("泛型类型[%s]未标注@CollectionName注解", entityClass.getName())
            );
        }
        String collectionName = annotation.value().trim();
        if (collectionName.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("泛型类型[%s]的@CollectionName注解value为空", entityClass.getName())
            );
        }
        return collectionName;
    }

    /**
     * 通用的 新增 方法
     * @param entity 实体
     * @return 唯一Key
     */
    public Long insert(T entity) {

        MilvusClientV2 client = null;
        try {
            // 如果主键类型为自增，必须为Null
            MilvusUtil.setAutoPrimaryKeyToNull(entity);

            // 根据实体中的CollectionName注解获取集合名称
            String collectionName = validateCollectionNameAnnotation();

            // 获取客户端
            client = pool.getClient(clientKey);

            // 构建新增数据 ， 并指定驼峰转下划线策略
            Gson gson = new GsonBuilder().setFieldNamingStrategy(new CamelToUnderlineNamingStrategy())
                    .setPrettyPrinting()
                    .create();
            String entityJson = gson.toJson(entity);
            List<JsonObject> data = Collections.singletonList(gson.fromJson(entityJson, JsonObject.class));

            // 构建请求信息
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(data)
                    .build();

            // 插入数据
            InsertResp insert = client.insert(insertReq);
            List<Object> primaryKeys = insert.getPrimaryKeys();
            return (Long) primaryKeys.get(0);
        } catch (Exception e) {
            log.error("操作Milvus数据库插入数据失败，原因: {}", e.getMessage(), e);
            // todo：异常代码结合项目生成
            throw new ServiceException(null);
        } finally {
            if (client != null) {
                pool.returnClient(clientKey,client);
            }
        }

    }

    /**
     * 通用的 根据主键删除 方法
     * @param id 主键
     * @return 唯一Key
     */
    public boolean deleteById(Long id) {

        MilvusClientV2 client = null;
        try {
            // 根据实体中的CollectionName注解获取集合名称
            String collectionName = validateCollectionNameAnnotation();

            // 获取Client对象
            client = pool.getClient(clientKey);

            // 删除操作
            DeleteResp bizSyns = client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .ids(Collections.singletonList(id))
                    .build());

            return bizSyns.getDeleteCnt() == 1;
        } catch (Exception e) {
            log.error("操作Milvus数据库删除数据失败，原因: {}", e.getMessage(), e);
            // todo：替换符合项目业务的异常代码
            throw new ServiceException(null);
        } finally {
            if (client != null) {
                pool.returnClient(clientKey,client);
            }
        }
    }

    /**
     * 根据id修改数据，Milvus没有修改方法，所以 先删除再插入
     * @param entity 实体
     * @return 修改结果
     */
    public Long updateById(T entity) {

        try {

            // 获取主键的值
            Long id = MilvusUtil.getPrimaryKeyValue(entity);

            // 删除数据
            deleteById(id);

            // 新增数据
            return insert(entity);
        } catch (Exception e) {
            log.error("milvus 更新失败，原因：{}", e.getMessage(), e);
            // todo：替换符合业务的异常代码
            throw new ServiceException(null);
        }
    }

    public PageResult<T> selectPage(PageParam param, MilvusLambdaQueryWrapper<T> wrapper) {

        String filter = wrapper.buildFilter();
        return selectPage(param, filter);
    }
    public PageResult<T> selectPage(PageParam param, String filter) {

        MilvusClientV2 client = null;
        try {
            PageResult<T> result = new PageResult<>();
            // 根据实体中的CollectionName注解获取集合名称
            String collectionName = validateCollectionNameAnnotation();

            // 获取客户端
            client = pool.getClient(clientKey);

            // 先查询总数据条数
            long count = queryCount(filter, collectionName, client);
            if (count == 0) {
                result.setList(new ArrayList<>());
                result.setTotal(count);
                return result;
            }

            result.setTotal(count);

            // 偏移量 = (当前页码 - 1) * 每页记录数（页码从 1 开始）
            long offset = (long) (param.getPageNo() - 1) * param.getPageSize();

            // 分页查询数据
            QueryReq countReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(filter) // 与分页查询的筛选条件保持一致
                    .offset(offset)
                    .limit(param.getPageSize())
                    .build();
            QueryResp countResp = client.query(countReq);

            // 数据封装
            completeResult(countResp, result);
            return result;
        } catch (Exception e) {
            log.error("操作Milvus数据库查询数据失败，原因: {}", e.getMessage(), e);
            // todo：替换符合项目的异常代码
            throw new ServiceException(null);
        } finally {
            if (client != null) {
                pool.returnClient(clientKey,client);
            }
        }
    }

    /**
     * 根据主键获取数据
     * @param id 主键值
     * @return 数据
     */
    public T getById(Long id) {

        MilvusClientV2 client = null;
        try {
            // 根据实体中的CollectionName注解获取集合名称
            String collectionName = validateCollectionNameAnnotation();

            // 获取客户端
            client = pool.getClient(clientKey);

            // 获取当前类主键的属性名
            String keyFieldName = MilvusUtil.getPrimaryKeyFieldName(entityClass);

            // 查询数据
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(keyFieldName + "==" + id)
                    .limit(1)
                    .build();
            QueryResp countResp = client.query(queryReq);

            // 数据封装
            List<T> resultList = completeResult(countResp);
            return CollectionUtils.isAnyEmpty(resultList) ? null : resultList.get(0);
        } catch (Exception e) {
            log.error("操作Milvus数据库查询数据失败，原因: {}", e.getMessage(), e);
            // todo：替换符合项目的异常代码
            throw new ServiceException(null);
        } finally {
            if (client != null) {
                pool.returnClient(clientKey,client);
            }
        }
    }

    private List<T> completeResult(QueryResp countResp) {

        List<T> dataList = new ArrayList<>();
        List<QueryResp.QueryResult> queryResults = countResp.getQueryResults();

        // 数据封装
        for (QueryResp.QueryResult queryResult : queryResults) {
            Map<String, Object> entity = queryResult.getEntity();
            String jsonStr = JSON.toJSONString(entity);
            T parseObject = JSON.parseObject(jsonStr, entityClass);
            dataList.add(parseObject);

        }
        return dataList;
    }

    private void completeResult(QueryResp countResp, PageResult<T> result) {

        List<T> dataList = new ArrayList<>();
        List<QueryResp.QueryResult> queryResults = countResp.getQueryResults();

        // 数据封装
        for (QueryResp.QueryResult queryResult : queryResults) {
            Map<String, Object> entity = queryResult.getEntity();
            String jsonStr = JSON.toJSONString(entity);
            T parseObject = JSON.parseObject(jsonStr, entityClass);
            dataList.add(parseObject);

        }
        result.setList(dataList);
    }

    private long queryCount(String filter, String collectionName, MilvusClientV2 client) {
        QueryReq countReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter(filter) // 与分页查询的筛选条件保持一致
                .outputFields(Collections.singletonList("count(*)")) // 关键：通过 count(*) 统计总数
                .build();
        QueryResp countResp = client.query(countReq);

        // 解析总记录数（count(*) 的结果是 Long 类型，需从返回的实体中提取）
        long totalCount = 0;
        if (!countResp.getQueryResults().isEmpty()) {
            // 取出第一条结果的 "count(*)" 字段值（统计结果只有一条）
            totalCount = (Long) countResp.getQueryResults().get(0).getEntity().get("count(*)");
        }
        return totalCount;
    }

}
