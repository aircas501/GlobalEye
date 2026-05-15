
package com.globaleyes.crawler.repository.satellite;

import com.globaleyes.crawler.pojo.entity.SatelliteCatalogData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 卫星目录数据 Repository 接口
 */
@Repository
public interface SatelliteCatalogDataRepository extends JpaRepository<SatelliteCatalogData, Long> {

    /**
     * 分页查询所有数据
     */
    Page<SatelliteCatalogData> findAll(Pageable pageable);

}
