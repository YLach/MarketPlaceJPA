package market;


import java.io.Serializable;

public class Item implements Serializable, Comparable<Item> {

    private final String name;
    private final float price;

    public Item(String name, float price) {
        this.name = name;
        this.price = price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Item)) return false;

        Item item = (Item) o;

        if (price != item.price) return false;
        return name.equals(item.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (price != +0.0f ? Float.floatToIntBits(price) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Item[" +
                "name : " + name +
                ", price : $" + price +
                ']';
    }

    @Override
    public int compareTo(Item o) {
        int cmp = this.getName().compareTo(o.getName());
        if (cmp == 0) {
            // If name equal, check price
            if (this.price < o.getPrice())
                cmp = -1;
            else if (this.price > o.getPrice())
                cmp = 1;
        }
        return cmp;
    }

    public String getName() {
        return name;
    }

    public float getPrice() {
        return price;
    }


}
