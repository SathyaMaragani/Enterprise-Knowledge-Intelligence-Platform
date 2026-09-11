package com.eip.backend.repository;
import com.eip.backend.entity.Document;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface DocumentRepository extends JpaRepository<Document, Integer> {
    List<Document> findByStatus(String status);
    List<Document> findByCategoryId(Integer categoryId);
    List<Document> findByOwnerId(Integer ownerId);

    /**
     * Of the given document ids, the ones this user owns or holds a READ grant on.
     * Admins bypass this entirely -- see DocumentAccessService.
     */
    @Query("""
        select d.id from Document d
        where d.id in :ids
          and (d.owner.id = :userId
               or exists (select 1 from DocumentPermission p
                          where p.document.id = d.id
                            and p.user.id = :userId
                            and p.permissionType = 'READ'))
        """)
    Set<Integer> findReadableIds(@Param("ids") Collection<Integer> ids,
                                @Param("userId") Integer userId);

    /**
     * Keyword candidates for search. Empty string means "no filter" -- avoids the
     * untyped-null-parameter problem Postgres hits with `:param is null` in JPQL.
     */
    @Query("""
        select d from Document d
        where (lower(d.title) like lower(concat('%', :q, '%'))
               or lower(d.description) like lower(concat('%', :q, '%')))
          and (:category = '' or d.category.name = :category)
          and (:status = '' or d.status = :status)
        """)
    List<Document> searchByKeyword(@Param("q") String q,
                                   @Param("category") String category,
                                   @Param("status") String status,
                                   Pageable pageable);
}
