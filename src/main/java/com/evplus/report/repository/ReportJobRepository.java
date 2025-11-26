package com.evplus.report.repository;

import com.evplus.report.model.entity.ReportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for ReportJob entities.
 * Provides CRUD operations and custom query methods for report job management.
 */
@Repository
public interface ReportJobRepository extends JpaRepository<ReportJob, String> {

    /**
     * Find all report jobs for a specific user, ordered by request date descending.
     * Used to display user's report history.
     *
     * @param userId the user ID to search for
     * @return list of report jobs ordered by most recent first
     */
    List<ReportJob> findByUserIdOrderByRequestedDateDesc(Integer userId);

    /**
     * Find all report jobs for a specific district with a given status.
     * Used by district administrators to monitor report generation.
     *
     * @param districtId the district ID to search for
     * @param statusCode the status code (0=QUEUED, 1=PROCESSING, 2=COMPLETED, 3=FAILED)
     * @return list of matching report jobs
     */
    List<ReportJob> findByDistrictIdAndStatusCode(Integer districtId, Integer statusCode);
}
