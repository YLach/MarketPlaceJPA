package market;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class ItemKey implements Serializable {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false, precision = 2)
    private float price;

    public ItemKey() { }

    public ItemKey(String name, float price) {
        this.name = name;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public float getPrice() {
        return price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;

        if (!(o instanceof ItemKey)) return false;
        ItemKey itemKey = (ItemKey) o;

        if (Float.compare(itemKey.price, price) != 0) return false;
        return name.equals(itemKey.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (price != +0.0f ? Float.floatToIntBits(price) : 0);
        return result;
    }
}