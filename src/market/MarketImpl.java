package market;


import bank.Account;
import bank.Bank;
import client.Trader;
import com.sun.org.apache.regexp.internal.RE;

import javax.persistence.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class MarketImpl extends UnicastRemoteObject implements Market {
    private static final String BANK = "Nordea";
    private static final int DEFAULT_LOCAL_REGISTRY_PORT_NUMBER = 1099;

    private Map<String, Trader> loggedIn = new HashMap<>();
    private AbstractMap<Item, Trader> wishList = new ConcurrentSkipListMap<>();
    private String bankname;
    Bank bankobj;

    private EntityManagerFactory emFactory;

    /**
     * Default constructor
     * @throws RemoteException
     */
    public MarketImpl() throws RemoteException {
        this(BANK, DEFAULT_LOCAL_REGISTRY_PORT_NUMBER);
    }

    /**
     * Constructor : to create the market remote object, we need to get first the
     * remote bank object
     * @param bankName
     * @param bankPort
     * @throws RemoteException
     */
    public MarketImpl(String bankName, int bankPort) throws RemoteException {
        super(); // To export the servant class

        // Create the Entity Manage Factory used to create the Entity Manager
        emFactory = Persistence.createEntityManagerFactory("market");

        // We get the reference on the remote bank object
        this.bankname = bankName;
        try {
            Registry bankRegistry;
            try {
                bankRegistry = LocateRegistry.getRegistry(bankPort);
                bankRegistry.list();
            } catch (RemoteException e) {
                bankRegistry = LocateRegistry.createRegistry(bankPort);
            }
            bankobj = (Bank) bankRegistry.lookup(bankname);

        } catch (Exception e) {
            System.err.println("The runtime failed: " + e.getMessage());
            System.exit(1);
        }
        System.out.println("Connected to bank: " + bankname);
    }

    private boolean isLoggedIn(String clientName) throws RemoteException {
        if (loggedIn.containsKey(clientName)) {
            try {
                loggedIn.get(clientName).getClientName(); //Remote call to check is still the same remote client
                return true;
            } catch (RemoteException e) {
                loggedIn.remove(clientName);
                return false;
            }
        }
        return false;
    }

    @Override
    public void login(Trader trader, String password) throws RemoteException, RejectedException {
        String clientName = trader.getClientName();

        // Check if already logged in
        if (isLoggedIn(clientName))
            throw new RejectedException("You are already logged in");

        EntityManager em = null;

        try {
            em = beginTransaction();

            // Check if user with the same name already exists
            User existingUser = em.find(User.class, clientName);
            if (existingUser == null)
                throw new RejectedException("Login failed: you are not registered on the market.");

            if (!existingUser.getPassword().equals(password))
                throw new RejectedException("Login failed: wrong password");

            loggedIn.put(clientName, trader);
            System.out.println("Trader " + clientName + " logged in on the market.");


            // Callback to send since last login ?
            Query query = em.createNamedQuery("FindItemsToAck", Item.class);
            query.setParameter("sellerName", clientName);
            List<Item> itemsToAck = query.getResultList();
            for (Item i : itemsToAck) {
                trader.callback(i.getToAcknowledge() + " " + i + " has/have been sold");
                i.setToAcknowledge(0);
                //em.createNamedQuery("UpdateAckItem").setParameter("sellerName", clientName).executeUpdate();
            }

        } finally {
            if (em != null)
                commitTransaction(em);
        }
    }

    @Override
    public void logout(String traderName) throws RemoteException, RejectedException {
        if (!isLoggedIn(traderName))
            throw new RejectedException("You are not logged in");

        loggedIn.remove(traderName);
        System.out.println("Trader " + traderName + " logged out from the market.");
    }

    @Override
    public synchronized void register(Trader trader, String password)
            throws RemoteException, RejectedException {
        String traderName = trader.getClientName();

        EntityManager em = null;
        User user;
        try {
            em = beginTransaction();

            // Check if user with the same name already exists
            User existingUser = em.find(User.class, traderName);
            if (existingUser != null)
                throw new RejectedException("Registration failed: Trader " + traderName + " already registered");

            // Not already registered
            // Check the password length
            if (password.length() < 8)
                throw new RejectedException("Invalid size of password : must contain at least 8 characters");

            // Register the new user
            user = new User(traderName, password);
            em.persist(user);

            // Logged in automatically
            loggedIn.put(traderName, trader);

            System.out.println("Trader " + traderName + " registered on the market.");

        } finally {
            if (em != null)
                commitTransaction(em);
        }
    }

    @Override
    public synchronized void unregister(Trader trader) throws RemoteException, RejectedException {
        String traderName = trader.getClientName();

        // Remove all items belonging to that trader
        if (!isLoggedIn(traderName))
            throw new RejectedException("Trader " + traderName + " not registered");

        EntityManager em = null;

        try {
            em = beginTransaction();

            // Remove all items belonging to this trader
            List<Item> itemsSeller = em.createNamedQuery("FindItemsBySeller", Item.class).
                    setParameter("sellerName", traderName).getResultList();

            for (Item i : itemsSeller) {
                em.remove(i);
            }

            // Remove all wishes from this trader
            for(Map.Entry<Item, Trader> entry : wishList.entrySet()) {
                if (entry.getValue().getClientName().equals(traderName))
                    wishList.remove(entry.getKey());
            }

            // Suppression from the persistence storage
            User userToUnregister = em.find(User.class, traderName);
            if (em != null)
                em.remove(userToUnregister);
            else
                throw new RejectedException("Unregistration failed: User " + traderName +
                " already unregistered");

        } finally {
            if (em != null)
                commitTransaction(em);

            // Remove the trader from the market
            loggedIn.remove(traderName);
            System.out.println("Trader " + traderName + " unregistered from the market.");
        }
    }

    @Override
    public void sell(Item itemToSell, Trader trader) throws RemoteException, RejectedException {
        String traderName = trader.getClientName();

        // Trader registered on the market ?
        if (!isLoggedIn(traderName))
            throw new RejectedException("Sell failed: you are not logged in / registered on the market");

        // Get an account ?
        Account account = bankobj.findAccount(trader.getClientName());
        if (account == null)
            throw new RejectedException("You cannot sell the item " + itemToSell  +
                    ": you do not get an account at bank " + bankname);

        EntityManager em = null;

        // Item to sell already on the market ?
        try {
            em = beginTransaction();

            Item item = em.find(Item.class, new ItemKey(itemToSell.getName(), itemToSell.getPrice()));

            if ( (item != null) && (!item.getSeller().getUsername().equals(trader.getClientName())) )
                throw new RejectedException("Sell failed: item " + itemToSell + " already on the market.");

            // Can be sold
            if (item != null)
                item.setAmount(item.getAmount() + itemToSell.getAmount());
            else {
                User seller = em.find(User.class, trader.getClientName());

                // Store a new Item persistently
                em.persist(new Item(itemToSell, seller));
            }

        } finally {
            if (em != null) {
                commitTransaction(em);

                System.out.println(itemToSell + " puts on the market by " + trader.getClientName());

                //Debugging
                for (Map.Entry<Item, Trader> entry : wishList.entrySet()) {
                    System.out.println("Wish from " + entry.getValue().getClientName() +
                            " : " + entry.getKey());
                }

                // Check if some buyers have placed a wish on that itemToSell
                for (Map.Entry<Item, Trader> entry : wishList.entrySet()) {
                    //System.out.println("[DEBUG] " + itemToSell.compareTo(entry.getKey()));
                    if ((itemToSell.compareTo(entry.getKey()) <= 0) &&
                            (entry.getKey().getName().equals(itemToSell.getName()))) {
                        // Send callback
                        try {
                            if (isLoggedIn(entry.getValue().getClientName())) {
                                entry.getValue().callback(itemToSell + " available on the market");
                                // Remove its wish ?
                                wishList.remove(entry.getKey());
                            }
                        } catch (RemoteException e) {}
                    }
                }
            }
        }
    }

    @Override
    public void buy(Item itemToBuy, Trader trader) throws RemoteException, RejectedException,
            bank.RejectedException {
        String traderName = trader.getClientName();

        // Trader registered on the market ?
        if (!isLoggedIn(traderName))
            throw new RejectedException("Buy failed: you are not logged in / registered on the market");

        EntityManager em = null;

        try {
            em = beginTransaction();

            // Item on the market ?
            Item itemToSell = em.find(Item.class, new ItemKey(itemToBuy.getName(), itemToBuy.getPrice()));
            if (itemToSell == null)
                throw new RejectedException("Buy failed: item " + itemToBuy + " no longer on the market.");

            // Enough amount of item ?
            if (itemToSell.getAmount() < itemToBuy.getAmount())
                throw new RejectedException("Bell failed: you cannot buy " + itemToBuy.getAmount() +
                        " items " + itemToBuy + " : there is only " + itemToSell.getAmount() +
                        " items remaining on the market");

            // Get an account ?
            Account accountBuyer = bankobj.findAccount(trader.getClientName());
            if (accountBuyer == null)
                throw new RejectedException("Buy failed: you cannot buy the item " + itemToBuy +
                        " : you do not get an account at bank " + bankname);

            // Enough money ?
            if (accountBuyer.getBalance() < (itemToSell.getPrice() * itemToBuy.getAmount()))
                throw new RejectedException("Buy failed: you cannot afford to buy " +
                        itemToBuy.getAmount() + " " + itemToBuy);

            // Yes
            String sellerName = itemToSell.getSeller().getUsername();
            Account accountSeller = bankobj.findAccount(sellerName);
            if (!(accountSeller == null))
                bankobj.withdraw(traderName, itemToSell.getPrice() * itemToBuy.getAmount());
            bankobj.deposit(sellerName, itemToSell.getPrice() * itemToBuy.getAmount());

            // Update stats
            User seller = itemToSell.getSeller();
            seller.setNbTotalItemsSold(seller.getNbTotalItemsSold() + itemToBuy.getAmount());
            User buyer = em.find(User.class, trader.getClientName());
            buyer.setNbTotalItemsBought(buyer.getNbTotalItemsBought() + itemToBuy.getAmount());


            // Callback
            if (!isLoggedIn(sellerName)) {
                itemToSell.setToAcknowledge(itemToSell.getToAcknowledge() + itemToBuy.getAmount());
                itemToSell.setAmount(itemToSell.getAmount() - itemToBuy.getAmount());
            } else {
                loggedIn.get(sellerName).callback(itemToBuy.getAmount() + " " + itemToBuy + " has/have been sold");

                if (itemToSell.getAmount() == itemToBuy.getAmount()) {
                    // Remove item from database
                    em.remove(itemToSell);
                } else {
                    itemToSell.setAmount(itemToSell.getAmount() - itemToBuy.getAmount());
                }
            }
        } finally {
            if (em != null) {
                commitTransaction(em);
                System.out.println(itemToBuy.getAmount() + " " + itemToBuy + " bought by " + trader.getClientName());
            }
        }
    }


    @Override
    public void wish(Item item, Trader trader) throws RemoteException, RejectedException,
            bank.RejectedException {
        String traderName = trader.getClientName();

        // Trader registered on the market ?
        if (!isLoggedIn(traderName))
            throw new RejectedException("You are not logged in / registered on the market");

        // Already did a wish for that item ?
        for (Map.Entry<Item, Trader> entry : wishList.entrySet()) {
            if ((entry.getKey().getName().equals(item.getName())) &&
                    (entry.getValue().equals(trader)))
                throw new RejectedException("You already placed a wish on " + item + " .");
        }

        // Someone else ?
        if (wishList.containsKey(item))
            throw new RejectedException("Someone else already placed the same wish on " + item + " .");

        wishList.put(item, trader);
        System.out.println("Wish from " + trader.getClientName() + " : " + item);

        /*System.out.println();
        for (Map.Entry<Item, Trader> entry : wishList.entrySet()) {
            System.out.println("Wish from " + entry.getValue().getClientName() + " : " + entry.getKey());
        }*/
    }

    @Override
    public ArrayList<Item> getAllItems() throws RemoteException {
        EntityManager em = null;
        List<Item> items = null;

        try {
            em = beginTransaction();

            items = em.createNamedQuery("AllItemsToSell", Item.class).getResultList();
            System.err.println("SIZE : " + items.size());
        } finally {
            if (em != null)
                commitTransaction(em);
            return new ArrayList<>(items);
        }

    }

    @Override
    public ArrayList getStats(String username) throws RemoteException, RejectedException {
        // Trader registered on the market ?
        if (!isLoggedIn(username))
            throw new RejectedException("You are not logged in / registered on the market");

        ArrayList stats = null;
        EntityManager em = null;

        try {
            em = beginTransaction();

            // Check if user with the same name already exists
            User existingUser = em.find(User.class, username);
            if (existingUser == null)
                throw new RejectedException("Get Statistics failed: user " +
                        username + " is not registered on the market.");

            stats = new ArrayList<String>(2);
            stats.add(INDEX_NB_TOTAL_ITEMS_BOUGHT, String.valueOf(existingUser.getNbTotalItemsBought()));
            stats.add(INDEX_NB_TOTAL_ITEMS_SOLD, String.valueOf(existingUser.getNbTotalItemsSold()));

        } finally {
            if (em != null)
                commitTransaction(em);
            return stats;
        }
    }

    // Transaction management
    private EntityManager beginTransaction()
    {
        EntityManager em = emFactory.createEntityManager();
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        return em;
    }

    private void commitTransaction(EntityManager em)
    {
        em.getTransaction().commit();
    }

}
