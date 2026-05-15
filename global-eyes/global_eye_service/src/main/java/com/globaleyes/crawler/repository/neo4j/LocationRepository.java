package com.globaleyes.crawler.repository.neo4j;

import com.globaleyes.crawler.pojo.entity.neo4j.LocationNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 地点Neo4j Repository
 * 用于操作Neo4j图数据库中的地点节点
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Repository
public interface LocationRepository extends Neo4jRepository<LocationNode, Long> {

    @Query("MATCH (l:Location) WHERE l.name = $name RETURN l LIMIT 1")
    Optional<LocationNode> findByName(@Param("name") String name);

    /**
     * 查询所有国家类型的地点
     *
     * @return 国家地点列表
     */
    @Query("MATCH (l:Location) WHERE l.type = 'country' RETURN l ORDER BY l.name")
    List<LocationNode> findAllCountries();
}
