package luckydrop.demo.draw.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80, nullable = false)
    private String name;

    @Column(length = 80, nullable = false)
    private String brand;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "retail_price")
    private Integer retailPrice;

    @OneToMany(mappedBy = "inventory", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<InventoryImage> images = new ArrayList<>();

    //private LocalDateTime created_at;

    @Builder
    public Inventory(String name,
                     String brand,
                     String description,
                     Integer retailPrice) {
        this.name = name;
        this.brand = brand;
        this.description = description;
        this.retailPrice = retailPrice;
    }
}
