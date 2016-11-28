package market;


import client.Trader;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Vector;

public interface Market extends Remote {
    final static int INDEX_NB_TOTAL_ITEMS_BOUGHT = 0;
    final static int INDEX_NB_TOTAL_ITEMS_SOLD = 1;

    void login(Trader trader, String password) throws RemoteException, RejectedException;

    void logout(String traderName) throws RemoteException, RejectedException;

    void register(Trader trader, String password) throws RemoteException, RejectedException;

    void unregister(Trader trader) throws RemoteException, RejectedException;

    void sell(Item item, Trader trader) throws RemoteException, RejectedException;

    void buy(Item item, Trader trader) throws RemoteException, RejectedException, bank.RejectedException;

    void wish(Item item, Trader trader) throws RemoteException, RejectedException, bank.RejectedException;

    ArrayList<Item> getAllItems() throws RemoteException;

    ArrayList<String> getStats(String username) throws RemoteException, RejectedException;
}
