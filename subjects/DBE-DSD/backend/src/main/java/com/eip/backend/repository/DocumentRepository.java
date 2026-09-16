package com.eip.backend.repository;
import com.eip.backend.entity.Document;
import org.springframework.data.domain.Page;
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

    /**
     * One page of the documents a user may read, filtered and counted in SQL.
     *
     * <p>The owner-or-READ-grant predicate repeats {@link #findReadableIds}: that
     * method checks a known set of ids, this one has to find them without loading
     * the table first. {@code testPagedReadableQueryAgreesWithReadableIds} keeps
     * the two identical. Admins pass {@code admin = true} and skip the predicate,
     * matching DocumentAccessService.
     *
     * <p>Empty strings mean "no filter", as in {@link #searchByKeyword}. {@code q}
     * matches title or description, case-insensitively.
     */
    @Query(value = """
        select d from Document d
        left join d.category c
        where (:admin = true
               or d.owner.id = :userId
               or exists (select 1 from DocumentPermission p
                          where p.document.id = d.id
                            and p.user.id = :userId
                            and p.permissionType = 'READ'))
          and (:category = '' or c.name = :category)
          and (:status = '' or d.status = :status)
          and (:q = '' or lower(d.title) like lower(concat('%', :q, '%'))
                       or lower(d.description) like lower(concat('%', :q, '%')))
        """,
        countQuery = """
        select count(d) from Document d
        left join d.category c
        where (:admin = true
               or d.owner.id = :userId
               or exists (select 1 from DocumentPermission p
                          where p.document.id = d.id
                            and p.user.id = :userId
                            and p.permissionType = 'READ'))
          and (:category = '' or c.name = :category)
          and (:status = '' or d.status = :status)
          and (:q = '' or lower(d.title) like lower(concat('%', :q, '%'))
                       or lower(d.description) like lower(concat('%', :q, '%')))
        """)
    Page<Document> findReadable(@Param("admin") boolean admin,
                                @Param("userId") Integer userId,
                                @Param("category") String category,
                                @Param("status") String status,
                                @Param("q") String q,
                                Pageable pageable);

    /**
     * Candidates for the Phase 1.7C TextHack lexical scan: every document passing
     * the category and status filters, in a stable order.
     *
     * <p>The category is joined explicitly with {@code left join}. Writing
     * {@code d.category.name} in the where clause instead would be an implicit
     * inner join, silently excluding documents that have no category at all --
     * even when no category filter was requested.
     */
    @Query("""
        select d from Document d
        left join d.category c
        where (:category = '' or c.name = :category)
          and (:status = '' or d.status = :status)
        order by d.id
        """)
    List<Document> findForLexicalScan(@Param("category") String category,
                                      @Param("status") String status,
                                      Pageable pageable);
}
