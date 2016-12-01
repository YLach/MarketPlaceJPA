package market;


import javax.persistence.*;
import java.io.Serializable;

@NamedQueries({
        @NamedQuery(
                name = "AllItemsToSell",
                query = "SELECT i FROM Items i WHERE i.amount > 0"
        ),

        @NamedQuery(
                name = "FindItemsBySeller",
                query = "SELECT i FROM Items i WHERE i.seller.username = :sellerName",
                lockMode = LockModeType.PESSIMISTIC_FORCE_INCREMENT
        ),
        @NamedQuery(
                name = "FindItemsToAck",
                query = "SELECT i FROM Items i WHERE i.seller.username = :sellerName AND i.toAcknowledge > 0",
                lockMode = LockModeType.PESSIMISTIC_FORCE_INCREMENT
        ),
        @NamedQuery(
                name = "UpdateAckItem",
                query = "UPDATE Items i SET i.toAcknowledge = 0 WHERE i.seller.username = :sellerName AND i.toAcknowledge > 0"
        )
})


@Entity(name = "Items")
public class Item implements Serializable, Comparable<Item> {

    @EmbeddedId
    private ItemKey itemKey;

    @OneToOne
    @JoinColumn(name = "seller", nullable = false)
    private User seller;

    private int amount = 0;
    private int toAcknowledge = 0; // Used to send callback (if the user is not connected when a item is sold)

    @Version
    @Column(name = "PESSLOCK")
    private int versionNum;

    public Item() {
        this("", 0f, 0);
    }

    public Item(String name, float price, int amount) {
        this.itemKey = new ItemKey(name, price);
        this.amount = amount;
        this.seller = null;
    }

    public Item(String name, float price, int amount, User seller) {
        this.itemKey = new ItemKey(name, price);
        this.amount = amount;
        this.seller = seller;
    }

    public Item(Item item, User seller) {
        this(item.getName(), item.getPrice(), item.getAmount(), seller);
    }

    public ItemKey getItemKey() {
        return itemKey;
    }

    public String getName() {
        return itemKey.getName();
    }

    public float getPrice() {
        return itemKey.getPrice();
    }

    public User getSeller() {
        return seller;
    }

    public int getAmount() {
        return amount;
    }

    public int getToAcknowledge() {
        return toAcknowledge;
    }

    public void setToAcknowledge(int toAcknowledge) {
        this.toAcknowledge = toAcknowledge;
    }

    public void setAmount(int amount) throws RejectedException {
        if (amount < 0)
            throw new RejectedException("Item amount update: Invalid amount");
        this.amount = amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Item)) return false;

        Item item = (Item) o;

        return item.getItemKey().equals(this.getItemKey());
    }

    @Override
    public int hashCode() {
        int result = itemKey.hashCode();
        result = 31 * result + amount;
        return result;
    }

    @Override
    public String toString() {
        return "Item[" +
                "name : " + itemKey.getName() +
                ", price : $" + itemKey.getPrice() +
                ", amount : " + amount +
                ']';
    }

    @Override
    public int compareTo(Item o) {
        int cmp = this.getName().compareTo(o.getName());
        if (cmp == 0) {
            // If name equal, check price
            if (this.itemKey.getPrice() < o.getPrice())
                cmp = -1;
            else if (this.itemKey.getPrice() > o.getPrice())
                cmp = 1;
        }
        return cmp;
    }
}
