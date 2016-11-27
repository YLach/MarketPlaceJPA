package market;


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "MarketUser")
public class User {
    @Id
    private String username;
    private String password;
    @Column(name = "nb_Total_Items_Sold")
    private int nbTotalItemsSold;
    @Column(name = "nb_Total_Items_Bought")
    private int nbTotalItemsBought;

    public User() {
        this("", "");
    }

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.nbTotalItemsBought = 0;
        this.nbTotalItemsBought = 0;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) throws RejectedException {
        if (password.length() < 8)
            throw new RejectedException("Password update : invalid size ! Must be at least 8 characters");

        this.password = password;
    }

    public int getNbTotalItemsSold() {
        return nbTotalItemsSold;
    }

    public void setNbTotalItemsSold(int nbTotalItemsSold) throws RejectedException {
        if (nbTotalItemsSold < 0)
            throw new RejectedException("Nb Items sold udpate : Invalid value");

        this.nbTotalItemsSold = nbTotalItemsSold;
    }

    public int getNbTotalItemsBought() {
        return nbTotalItemsBought;
    }

    public void setNbTotalItemsBought(int nbTotalItemsBought) throws RejectedException {
        if (nbTotalItemsBought < 0)
            throw new RejectedException("Nb Items bought udpate : Invalid value");

        this.nbTotalItemsBought = nbTotalItemsBought;
    }
}
