package com.example.userservice.repository;

import com.example.userservice.entity.User;
import com.example.userservice.entity.UserAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional // Tự động Rollback sau mỗi bài test để không làm bẩn DB
class UserAddressRepositoryTest {

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private UserRepository userRepository;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        // Dọn dẹp dữ liệu cũ trước mỗi ca test
        userAddressRepository.deleteAll();

        // Tạo 1 User mẫu làm chủ sở hữu địa chỉ
        sampleUser = new User();
        sampleUser.setEmail("crud.test@example.com");
        sampleUser.setPasswordHash("hashed_secret_pw");
        sampleUser.setFullName("Nguyen Van Test");
        sampleUser.setPhone("0901234567");
        sampleUser = userRepository.save(sampleUser);
    }

    // ==============================================================
    // 1. CREATE (TẠO MỚI)
    // ==============================================================
    @Test
    @DisplayName("C - Create: Lưu địa chỉ mới thành công và sinh ID tự động")
    void testCreateAddress() {
        // ARRANGE: Tạo đối tượng UserAddress mới
        UserAddress address = new UserAddress(
                sampleUser,
                "Nguyen Van Test",
                "0901234567",
                "123 Duong Le Loi",
                "Da Nang",
                true
        );

        // ACT: Gọi hàm save()
        UserAddress saved = userAddressRepository.save(address);

        // ASSERT: Kiểm tra xem đã sinh UUID và lưu đúng thông tin chưa
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRecipientName()).isEqualTo("Nguyen Van Test");
        assertThat(saved.getCity()).isEqualTo("Da Nang");
        assertThat(saved.isDefault()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // ==============================================================
    // 2. READ (ĐỌC / TÌM KIẾM)
    // ==============================================================
    @Test
    @DisplayName("R - Read: Tìm địa chỉ theo ID và theo UserId")
    void testReadAddress() {
        // ARRANGE: Lưu trước 1 địa chỉ vào DB
        UserAddress address = new UserAddress(
                sampleUser,
                "Tran Thi B",
                "0912345678",
                "456 Duong Nguyen Hue",
                "TP Ho Chi Minh",
                false
        );
        UserAddress saved = userAddressRepository.save(address);

        // ACT 1: Tìm theo Primary Key (findById)
        Optional<UserAddress> foundById = userAddressRepository.findById(saved.getId());

        // ASSERT 1
        assertThat(foundById).isPresent();
        assertThat(foundById.get().getStreetAddress()).isEqualTo("456 Duong Nguyen Hue");

        // ACT 2: Tìm theo UserId (findByUserId)
        List<UserAddress> list = userAddressRepository.findByUserId(sampleUser.getId());

        // ASSERT 2
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCity()).isEqualTo("TP Ho Chi Minh");
    }

    // ==============================================================
    // 3. UPDATE (CẬP NHẬT)
    // ==============================================================
    @Test
    @DisplayName("U - Update: Cập nhật thông tin địa chỉ và lưu lại")
    void testUpdateAddress() {
        // ARRANGE: Tạo và lưu địa chỉ ban đầu
        UserAddress address = new UserAddress(
                sampleUser,
                "Nguoi Nhan Cu",
                "0900000000",
                "So 1 Cu",
                "Ha Noi",
                false
        );
        UserAddress saved = userAddressRepository.save(address);
        UUID addressId = saved.getId();

        // ACT: Đổi thông tin và gọi save() lại
        saved.setRecipientName("Nguoi Nhan Moi");
        saved.setPhone("0999999999");
        saved.setStreetAddress("So 99 Moi");
        saved.setDefault(true);
        userAddressRepository.save(saved);

        // ASSERT: Lấy lại từ DB để kiểm chứng xem đã cập nhật chưa
        Optional<UserAddress> updated = userAddressRepository.findById(addressId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getRecipientName()).isEqualTo("Nguoi Nhan Moi");
        assertThat(updated.get().getPhone()).isEqualTo("0999999999");
        assertThat(updated.get().getStreetAddress()).isEqualTo("So 99 Moi");
        assertThat(updated.get().isDefault()).isTrue();
    }

    // ==============================================================
    // 4. DELETE (XÓA)
    // ==============================================================
    @Test
    @DisplayName("D - Delete: Xóa địa chỉ theo ID")
    void testDeleteAddress() {
        // ARRANGE: Tạo và lưu 1 địa chỉ
        UserAddress address = new UserAddress(
                sampleUser,
                "Can Xoa",
                "0901",
                "Dia chi rac",
                "Can Tho",
                false
        );
        UserAddress saved = userAddressRepository.save(address);
        UUID addressId = saved.getId();

        // Đảm bảo ban đầu có tồn tại
        assertThat(userAddressRepository.existsById(addressId)).isTrue();

        // ACT: Thực hiện xóa
        userAddressRepository.deleteById(addressId);

        // ASSERT: Kiểm tra lại xem bản ghi đã biến mất khỏi DB chưa
        Optional<UserAddress> deleted = userAddressRepository.findById(addressId);
        assertThat(deleted).isEmpty();
        assertThat(userAddressRepository.existsById(addressId)).isFalse();
    }
}

