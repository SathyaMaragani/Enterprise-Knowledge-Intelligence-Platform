package com.eip.backend.repository;
import com.eip.backend.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Integer> {
    List<SearchHistory> findByUserId(Integer userId);

    /** A user's 50 latest searches, newest first; ties on time go to the later insert. */
    List<SearchHistory> findTop50ByUser_IdOrderByCreatedAtDescIdDesc(Integer userId);
}