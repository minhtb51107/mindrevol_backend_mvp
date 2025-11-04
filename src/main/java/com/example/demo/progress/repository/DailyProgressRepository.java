package com.example.demo.progress.repository;

import com.example.demo.progress.entity.DailyProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyProgressRepository extends JpaRepository<DailyProgress, Long> {

    Optional<DailyProgress> findByPlanMemberIdAndDate(Integer planMemberId, LocalDate date);

    // Truy vấn này được dùng cho service thông báo (NotificationService)
    @Query("SELECT dp FROM DailyProgress dp " +
           "JOIN FETCH dp.planMember pm " +
           "JOIN FETCH pm.user u " +
           "JOIN FETCH pm.plan p " +
           "WHERE dp.date = :date AND dp.completed = false")
    List<DailyProgress> findPendingProgressForReminders(@Param("date") LocalDate date);
    
    // (Giả sử bạn có một truy vấn tương tự như thế này dựa trên log lỗi, 
    // nếu không, hãy đảm bảo BẤT KỲ @Query nào cũng KHÔNG CÓ "dp.comments" hoặc "dp.reactions")

    /* * === KHỐI GÂY LỖI ĐÃ BỊ XÓA HOẶC SỬA ===
     * Dựa trên log, bạn có một query tên là "findByIdWithDetails" hoặc tương tự.
     * Nó trông giống như thế này:
     * * @Query("SELECT dp FROM DailyProgress dp 
     * LEFT JOIN FETCH dp.planMember pm 
     * LEFT JOIN FETCH pm.user 
     * LEFT JOIN FETCH pm.plan p 
     * LEFT JOIN FETCH dp.comments c  <-- LỖI
     * LEFT JOIN FETCH c.author ca
     * LEFT JOIN FETCH dp.reactions r <-- LỖI
     * LEFT JOIN FETCH r.user ru 
     * WHERE dp.id = :id")
     * Optional<DailyProgress> findByIdWithDetails(@Param("id") Long id);
     *
     * Chúng ta phải xóa các join "comments" và "reactions" khỏi nó.
     * Nếu bạn không thể tìm thấy query này, hãy đảm bảo không có query nào
     * tham chiếu đến "comments" hoặc "reactions" trong tệp này.
     * * NẾU TỆP GỐC CỦA BẠN CÓ QUERY TƯƠNG TỰ NHƯ DƯỚI ĐÂY, HÃY THAY THẾ NÓ:
     */

    // VÍ DỤ: Sửa query findByIdWithDetails (nếu nó tồn tại)
    @Query("SELECT dp FROM DailyProgress dp " +
           "LEFT JOIN FETCH dp.planMember pm " +
           "LEFT JOIN FETCH pm.user " +
           "LEFT JOIN FETCH pm.plan p " +
           // "LEFT JOIN FETCH dp.comments c " + // <-- ĐÃ XÓA
           // "LEFT JOIN FETCH c.author ca " + // <-- ĐÃ XÓA
           // "LEFT JOIN FETCH dp.reactions r " + // <-- ĐÃ XÓA
           // "LEFT JOIN FETCH r.user ru " + // <-- ĐÃ XÓA
           "WHERE dp.id = :id")
    Optional<DailyProgress> findByIdWithDetails(@Param("id") Long id);
    
 // THÊM PHƯƠNG THỨC NÀY
    List<DailyProgress> findAllByPlanMemberIdIn(List<Integer> memberIds);
}