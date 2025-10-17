# Spring Boot 整合 Milvus 向量数据库：CRUD封装

> 本文将详细介绍如何在 Spring Boot 项目中封装 Milvus 向量数据库，实现类似 MyBatis-Plus 的优雅操作体验。包含连接池配置、泛型封装、Lambda 查询构造器等核心功能。

## 前言

在 AI 应用开发中，向量数据库扮演着越来越重要的角色。Milvus 作为一款开源的向量数据库，提供了高性能的向量相似度搜索能力。然而，官方提供的 Java SDK 使用起来相对底层，缺乏类似 MyBatis-Plus 这样的ORM框架的便利性。

本文将分享我在实际项目中对 Milvus 的封装经验，实现了一套类似 MyBatis-Plus 的操作模式，让向量数据库的操作变得更加简单直观。

## 技术栈

- **Spring Boot 3.x** - 主流 Java 开发框架
- **Milvus 2.x** - 开源向量数据库
- **Hutool** - Java 工具类库
- **MyBatis-Plus** - 借鉴其 Lambda 表达式设计理念
- **Gson** - JSON 序列化
- **FastJSON** - JSON 处理

## 整体架构设计

### 1. 核心组件概览

```txt
milvus-encapsulation/
├── annotation/
│   ├── CollectionName.java     # 集合名称注解
│   └── PrimaryKey.java         # 主键注解
├── config/                      # 配置相关
│   └── MilvusConfig.java       # 连接池配置  
├── strategy/
│		└── CamelToUnderlineNamingStrategy.java  # 驼峰转下划线
├── service/milvus/             # 核心封装
│   ├── MilvusBaseService.java  # 基础服务类
│   └── MilvusLambdaQueryWrapper.java  # Lambda查询构造器
└── util/
    └── MilvusUtil.java         # 工具类
```

### 2. 设计思路

1. **注解驱动**：通过自定义注解标识实体类与 Milvus Collection 的映射关系
2. **泛型封装**：利用 Java 泛型实现类型安全的 CRUD 操作
3. **Lambda 表达式**：借鉴 MyBatis-Plus 的设计，实现类型安全的查询构造
4. **连接池管理**：集成 Milvus 官方连接池，提升性能
5. **命名策略**：自动处理 Java 驼峰命名与 Milvus 下划线命名的转换

## 核心实现详解

### 1. 连接池配置

首先，需要配置 Milvus 连接池

```java
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.token}")
    private String token;

    @Value("${milvus.pool.max-idle-per-key:10}")
    private int maxIdlePerKey;

    @Value("${milvus.pool.max-total-per-key:20}")
    private int maxTotalPerKey;

    @Value("${milvus.pool.max-total:100}")
    private int maxTotal;

    @Value("${milvus.pool.wait-duration:5}")
    private long waitDuration;

    @Value("${milvus.pool.evictable-duration:30}")
    private long evictableDuration;

    /**
     * 初始化Milvus连接池
     */
    @Bean(destroyMethod = "close")
    public MilvusClientV2Pool milvusClientPool() {
        try {
            // 创建连接参数
            ConnectConfig connectConfig = ConnectConfig.builder()
                    .uri(host + ":" + port)
                    .token(token)
                    .build();

            // 配置连接池参数
            PoolConfig poolConfig = PoolConfig.builder()
                    .maxIdlePerKey(maxIdlePerKey)
                    .maxTotalPerKey(maxTotalPerKey)
                    .maxTotal(maxTotal)
                    .maxBlockWaitDuration(Duration.ofSeconds(waitDuration))
                    .minEvictableIdleDuration(Duration.ofSeconds(evictableDuration))
                    .build();

            return new MilvusClientV2Pool(poolConfig, connectConfig);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
```

配置文件示例：

```yaml
# Milvus 配置
milvus:
  host: your host
  port: your port
  token: your token
  client-key: client
  pool:
    max-idle-per-key: 20      # 每个key的最大空闲连接数
    max-total-per-key: 50     # 每个key的最大连接数
    max-total: 100            # 连接池最大连接数
    wait-duration: 60         # 获取连接的最大等待时间（秒）
    evictable-duration: 120   # 连接空闲多久后可以被回收（秒）
```

### 2. 实体类注解设计

我设计了两个核心注解来建立 Java 实体与 Milvus Collection 的映射关系：

#### CollectionName 注解

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface CollectionName {
    String value() default "";
}
```

#### PrimaryKey 注解

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
public @interface PrimaryKey {
    String value() default "";
    String type() default "auto";  // 自增主键
}
```

#### 实体类示例

```java
@CollectionName("biz_samples")  // 指定对应的 Milvus Collection 名称
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SamplesDO {

    /**
     * 唯一id
     */
    @PrimaryKey  // 标识主键字段
    private Long sampleId;

    /**
     * 智能体id
     */
    private Long agentId;

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 问题
     */
    private String sampleQuestion;

    /**
     * 答案
     */
    private String sampleAnswer;

    /**
     * 向量数据
     */
    private float[] sampleVector;

    /**
     * 状态
     */
    private String sampleStatus;

    /**
     * 创建时间
     */
    private Long createdTime;

    /**
     * 更新时间
     */
    private Long updatedTime;
}
```

### 3. 命名策略处理

由于 Java 习惯使用驼峰命名，而 数据库 推荐使用下划线命名，所以需要一个自动转换的策略：

```java
import cn.hutool.core.text.CharSequenceUtil;
import com.google.gson.FieldNamingStrategy;

import java.lang.reflect.Field;
public class CamelToUnderlineNamingStrategy implements FieldNamingStrategy {

    @Override
    public String translateName(Field field) {
        // 获取 Java 实体的原始字段名（如 "userName"）
        String originalFieldName = field.getName();
        // 调用 CharSequenceUtil.toSymbolCase 转为下划线名（如 "user_name"）
        return CharSequenceUtil.toSymbolCase(originalFieldName, '_');
    }
}
```

### 4. 基础服务封装

这是最核心的部分，我封装了一个泛型的基础服务类 `MilvusBaseService<T>`：

```java
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
    protected MilvusBaseService() {
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
}
```

#### 在MilvusBaseService中封装 核心 CRUD 方法实现

**新增操作：**

```java
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
        Gson gson = new GsonBuilder()
                .setFieldNamingStrategy(new CamelToUnderlineNamingStrategy())
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
        throw new ServiceException(ErrorCodeConstants.MILVUS_PRIMARY_KEY_ERROR);
    } finally {
        if (client != null) {
            pool.returnClient(clientKey, client);
        }
    }
}
```

**查询操作：**

```java
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
        throw new ServiceException(ErrorCodeConstants.SYNS_QUERY_ERROR);
    } finally {
        if (client != null) {
            pool.returnClient(clientKey, client);
        }
    }
}
```

**分页查询：**

```java
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
        throw new ServiceException(ErrorCodeConstants.SYNS_QUERY_ERROR);
    } finally {
        if (client != null) {
            pool.returnClient(clientKey, client);
        }
    }
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
```

**更新操作：**

Milvus 原生不支持更新操作，所以我采用**先删除后插入**的策略，这里存在事物的问题，Milvus 对事务有一定的支持，但并不完全支持传统意义上的 ACID 事务。
这里后续需要优化

```java
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
        throw new ServiceException(ErrorCodeConstants.MILVUS_PRIMARY_KEY_ERROR);
    }
}
```

### 5. Lambda 查询构造器

我实现了一个类似 MyBatis-Plus 的 LambdaQueryWrapper：由于目前业务并不复杂，所以只实现了基础的查询条件

```java
public class MilvusLambdaQueryWrapper<T> {

    // 存储查询条件，格式如 "id > 100"、"name like '%test%'"
    private final List<String> conditions = new ArrayList<>();

    /**
     * 等于条件 (field = value)
     * @param column 字段Lambda表达式，如 User::getId
     * @param value 值
     * @return 自身实例，支持链式调用
     */
    public <R> MilvusLambdaQueryWrapper<T> eqIfPresent(SFunction<T, R> column, Object value) {
        if (value == null) {
            return this;
        }
        String columnName = getColumnName(column);
        conditions.add(buildCondition(columnName, "==", value));
        return this;
    }

    /**
     * 模糊查询 (field like '%value%')
     * Milvus的like语法为 field like "%value%"
     * @param column 字段Lambda表达式
     * @param value 模糊匹配值
     * @return 自身实例
     */
    public MilvusLambdaQueryWrapper<T> likeIfPresent(SFunction<T, String> column, String value) {
        if (StrUtil.isEmpty(value)) {
            return this;
        }
        String columnName = getColumnName(column);
        conditions.add(String.format("%s like \"%%%s%%\"", columnName, value));
        return this;
    }

    /**
     * 从Lambda表达式中获取字段名
     * @param column 字段Lambda表达式，如 User::getName
     * @return 字段名
     */
    private <R> String getColumnName(SFunction<T, R> column) {
        // 利用Mybatis-plus的工具类解析属性
        String methodName = LambdaUtils.extract(column).getImplMethodName();

        // 解析getter方法名为字段名
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return CharSequenceUtil.toSymbolCase(StrUtil.lowerFirst(methodName.substring(3)), '_');
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            return CharSequenceUtil.toSymbolCase(StrUtil.lowerFirst(methodName.substring(2)), '_');
        }

        throw new IllegalArgumentException("无效的字段表达式: " + methodName);
    }

    /**
     * 拼接查询条件，生成Milvus兼容的过滤字符串
     * @return 过滤条件字符串，如 "id > 100 and name like '%test%'"
     */
    public String buildFilter() {
        if (conditions.isEmpty()) {
            return "";
        }
        // Milvus的多个条件用 and 连接
        return String.join(" and ", conditions);
    }
}
```

### 6. 工具类实现

实用的工具类：

```java
public class MilvusUtil {

    /**
     * 静态方法：解析对象中带有 @PrimaryKey 注解的字段，并返回该字段的值
     *
     * @param entity 待解析的实体对象（不能为 null）
     * @return 主键字段的值（Long 类型，若字段是 int/Integer，会自动装箱为 Long）
     * @throws IllegalArgumentException 若对象为 null、无主键、多主键、字段类型不兼容时抛出
     * @throws IllegalAccessException   若主键字段无访问权限（private 未处理）时抛出
     */
    public static Long getPrimaryKeyValue(Object entity) throws IllegalAccessException {
        // 1. 校验对象非 null
        if (entity == null) {
            throw new IllegalArgumentException("实体对象不能为 null，无法解析 @PrimaryKey 注解");
        }

        Class<?> entityClass = entity.getClass();
        Field primaryKeyField = null; // 存储找到的主键字段

        // 2. 扫描对象的所有字段（包括父类的字段，支持继承）
        Class<?> currentClass = entityClass;
        while (currentClass != null) { // 循环遍历父类，直到 Object 类
            Field[] fields = currentClass.getDeclaredFields(); // 获取当前类的所有字段（包括 private）
            for (Field field : fields) {
                // 判断字段是否标注 @PrimaryKey 注解
                if (field.isAnnotationPresent(PrimaryKey.class)) {
                    // 如果之前已经找到过主键字段，说明存在多主键情况
                    if (primaryKeyField != null) {
                        throw new IllegalArgumentException(
                                String.format("实体类[%s]存在多个@PrimaryKey注解，只能有一个主键字段", entityClass.getName())
                        );
                    }
                    primaryKeyField = field; // 记录主键字段
                }
            }
            currentClass = currentClass.getSuperclass(); // 继续向父类查找
        }

        // 3. 校验是否找到了主键字段
        if (primaryKeyField == null) {
            throw new IllegalArgumentException(
                    String.format("实体类[%s]未找到@PrimaryKey注解的字段", entityClass.getName())
            );
        }

        // 4. 获取主键字段的值
        primaryKeyField.setAccessible(true); // 允许访问 private 字段
        Object fieldValue = primaryKeyField.get(entity); // 获取字段值

        // 5. 校验字段值非 null
        if (fieldValue == null) {
            throw new IllegalArgumentException(
                    String.format("实体类[%s]的主键字段[%s]值为 null", entityClass.getName(), primaryKeyField.getName())
            );
        }

        // 6. 类型转换：支持 Long、Integer、int 类型，统一返回 Long
        if (fieldValue instanceof Long) {
            return (Long) fieldValue;
        } else if (fieldValue instanceof Integer) {
            return ((Integer) fieldValue).longValue();
        } else if (fieldValue instanceof int.class || fieldValue instanceof Integer) {
            return ((Integer) fieldValue).longValue();
        } else {
            throw new IllegalArgumentException(
                    String.format("实体类[%s]的主键字段[%s]类型必须是Long或Integer，当前类型为[%s]",
                            entityClass.getName(), primaryKeyField.getName(), fieldValue.getClass().getName())
            );
        }
    }
}
```

## 业务层使用示例

### 1. Service 接口定义

```java
public interface SamplesService {

    /**
     * 创建向量数据库样例库
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSamples(@Valid SamplesSaveReqVO createReqVO);

    /**
     * 删除向量数据库样例库
     *
     * @param sampleId 编号
     * @return 是否删除成功
     */
    boolean deleteSamples(Long sampleId);

    /**
     * 更新向量数据库样例库
     *
     * @param updateReqVO 更新信息
     */
    void updateSamples(SamplesSaveReqVO updateReqVO);

    /**
     * 获取向量数据库样例库分页
     *
     * @param pageReqVO 分页查询
     * @return 向量数据库样例库分页
     */
    PageResult<SamplesDO> getSamplesPage(SamplesPageReqVO pageReqVO);

    /**
     * 获取样例库信息
     * @param sampleId 主键id
     * @return 数据
     */
    SamplesDO getSampleInfo(Long sampleId);
}
```

### 2. Service 实现类

```java
@Slf4j
@Service
public class SamplesServiceImpl extends MilvusBaseService<SamplesDO> implements SamplesService {

    @Resource
    private EmbeddingModelApi embeddingModelApi;

    @Value("${assistant.embedding-model-id}")
    private Long embeddingModelId;

    @Override
    public Long createSamples(SamplesSaveReqVO createReqVO) {
        SamplesDO samplesDO = getSamplesDO(createReqVO);
        return insert(samplesDO);
    }

    @Override
    public boolean deleteSamples(Long sampleId) {
        return deleteById(sampleId);
    }

    @Override
    public void updateSamples(SamplesSaveReqVO updateReqVO) {
        SamplesDO samplesDO = getSamplesDO(updateReqVO);
        updateById(samplesDO);
    }

    @Override
    public PageResult<SamplesDO> getSamplesPage(SamplesPageReqVO pageReqVO) {
        // 构建请求条件
        MilvusLambdaQueryWrapper<SamplesDO> wrapper = new MilvusLambdaQueryWrapper<SamplesDO>()
                .likeIfPresent(SamplesDO::getAgentName, pageReqVO.getAgentName())
                .likeIfPresent(SamplesDO::getSampleQuestion, pageReqVO.getSampleQuestion())
                .likeIfPresent(SamplesDO::getSampleAnswer, pageReqVO.getSampleAnswer());

        // 分页查询
        return selectPage(pageReqVO, wrapper);
    }

    @Override
    public SamplesDO getSampleInfo(Long sampleId) {
        return getById(sampleId);
    }

    private SamplesDO getSamplesDO(SamplesSaveReqVO createReqVO) {
        SamplesDO samplesDO = BeanUtils.toBean(createReqVO, SamplesDO.class);

        // 生成问题字段的向量（这是AI应用特有的逻辑）
        String questionText = samplesDO.getSampleQuestion();
        float[] embeddingResult = embeddingModelApi.getEmbeddingResult(questionText, embeddingModelId);
        samplesDO.setSampleVector(embeddingResult);

        // 默认时间
        long currentTime = System.currentTimeMillis() / 1000;
        samplesDO.setCreatedTime(currentTime);
        samplesDO.setUpdatedTime(currentTime);
        return samplesDO;
    }
}
```

### 3. Controller 层

```java
@Tag(name = "管理后台 - 样例库")
@RestController
@RequestMapping("/helper/samples")
@Validated
public class SamplesController {

    @Resource
    private SamplesService samplesService;

    @GetMapping("/get")
    @Operation(summary = "获得样例库信息")
    @Parameter(name = "sampleId", description = "编号", required = true, example = "1024")
    public CommonResult<SamplesRespVO> getInfo(@RequestParam("sampleId") Long sampleId) {
        SamplesDO info = samplesService.getSampleInfo(sampleId);
        return success(BeanUtils.toBean(info, SamplesRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建样例记录")
    public CommonResult<Long> createSamples(@Valid @RequestBody SamplesSaveReqVO createReqVO) {
        return success(samplesService.createSamples(createReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除样例记录")
    @Parameter(name = "sampleId", description = "编号", required = true)
    public CommonResult<Boolean> deleteSamples(@RequestParam("sampleId") Long sampleId) {
        samplesService.deleteSamples(sampleId);
        return success(true);
    }

    @PutMapping("/update")
    @Operation(summary = "修改样例记录")
    public CommonResult<Boolean> updateSamples(@Valid @RequestBody SamplesSaveReqVO updateReqVO) {
        samplesService.updateSamples(updateReqVO);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询样例记录")
    public CommonResult<PageResult<SamplesRespVO>> getSamplesPage(@Valid SamplesPageReqVO pageReqVO) {
        PageResult<SamplesDO> pageResult = samplesService.getSamplesPage(pageReqVO);
        PageResult<SamplesRespVO> result = BeanUtils.toBean(pageResult, SamplesRespVO.class);
        for (SamplesRespVO respVO : result.getList()) {
            respVO.setCreatedTime(respVO.getCreatedTime() * 1000);
            respVO.setUpdatedTime(respVO.getUpdatedTime() * 1000);
        }
        return success(result);
    }
}
```

## 特性

### 1. 多实体支持

每个实体类对应不同的 Milvus Collection：

```java
// 样例库实体
@CollectionName("biz_samples")
public class SamplesDO {
    @PrimaryKey
    private Long sampleId;
    // ... 其他字段
}

// 同义词实体
@CollectionName("biz_syns")
public class SynsDO {
    @PrimaryKey
    private Long synId;
    // ... 其他字段
}

// 指标实体
@CollectionName("biz_indexes")
public class IndexesDO {
    @PrimaryKey
    private Long indexId;
    // ... 其他字段
}
```

### 2. 灵活的查询构造

Lambda 查询构造器支持多种查询条件：

```java
// 等于条件
wrapper.eqIfPresent(SamplesDO::getAgentId, 123L);

// 不等于条件
wrapper.ne(SamplesDO::getStatus, "deleted");

// 模糊查询
wrapper.likeIfPresent(SamplesDO::getAgentName, "测试");

// 范围查询
wrapper.gt(SamplesDO::getCreatedTime, startTime)
       .le(SamplesDO::getCreatedTime, endTime);

// IN 查询
wrapper.in(SamplesDO::getStatus, Arrays.asList("active", "pending"));

// 组合条件
String filter = wrapper.buildFilter(); // 生成: agent_name like "%测试%" and status == "active"
```



## 配置文件

```yaml
# Milvus相关配置
milvus:
  host: ${MILVUS_HOST:http://milvus-cluster.default.svc.cluster.local}
  port: ${MILVUS_PORT:19530}
  token: ${MILVUS_TOKEN:your-production-token}
  client-key: ${MILVUS_CLIENT_KEY:production-client}
  pool:
    max-idle-per-key: ${MILVUS_POOL_MAX_IDLE:50}
    max-total-per-key: ${MILVUS_POOL_MAX_TOTAL:100}
    max-total: ${MILVUS_POOL_MAX_GLOBAL:500}
    wait-duration: ${MILVUS_POOL_WAIT:30}
    evictable-duration: ${MILVUS_POOL_EVICT:300}
```

## 使用便利性

**传统方式 vs 封装后：**

```java
// 底层SDK
MilvusClientV2 client = new MilvusClientV2(connectConfig);
InsertReq insertReq = InsertReq.builder()
        .collectionName("biz_samples")
        .data(dataList)
        .build();
InsertResp response = client.insert(insertReq);

// 封装后（一行代码）
Long sampleId = samplesService.createSamples(reqVO);
```



> 工作里遇到个棘手问题，翻了圈没找到成熟好用的框架，又赶时间，就自己整理了份代码先解决需求。它肯定比不上大佬框架的周全，但能帮上有同样困扰的朋友落地业务。也盼着早点有成熟工具出来，省得大家重复踩坑～ 要是这份代码对你有用，或者有优化建议，随时交流！



## 参考资料

- [Milvus 官方文档](https://milvus.io/docs)
- [Milvus Java SDK](https://milvus.io/api-reference/java/v2.5.x/About.md)
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Hutool 官方文档](https://hutool.cn/)



**项目地址**：https://github.com/Jucunqi/milvus-encapsulation

本文示例代码已上传至 GitHub，欢迎 Star 和 Fork！

