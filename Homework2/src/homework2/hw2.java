package homework2;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//Withdraw could be Ordinary or Preferred
enum WithdrawType {
    ORDINARY,PREFERRED;
}

class Account{
    private int accountNumber; // To hold index in accountArray where this Account is stored
    private long balance;
    private final Lock lock;
    private int preferredWithdrawCount;//If there is Preferred Withdraw for this account then it will get preference
    private final Condition ordinaryWithdraw;//Signal if there is no preferred withdraw waiting
    private final Condition preferredWithdraw;//Signal preferably over ordinary withdraw

    Account(int accountNumber,long amount){
        this.accountNumber = accountNumber;
        this.balance = amount;
        this.lock = new ReentrantLock();
        this.preferredWithdrawCount = 0;
        this.ordinaryWithdraw = lock.newCondition();
        this.preferredWithdraw = lock.newCondition();
    }
    //Method to acquire current balance in account
    public long getBalance() {
        lock.lock();
        try {
            return balance;
        }finally {
            lock.unlock();
        }
    }
    //Method to deposit money into the account
    public long deposit(long amount){
        lock.lock();
        try {
            balance += amount;
            //Signal Preferred withdraw if any
            if(preferredWithdrawCount>0) {
                preferredWithdraw.signalAll();
            }else {
                ordinaryWithdraw.signalAll();//There is no preferred withdraw waiting, go for ordinary withdraw
            }
            System.out.println("Deposited $"+amount+" into  Account "+accountNumber+". New balance is $"+balance);
            return balance;
        }finally {
            lock.unlock();
        }
    }
    public long withdraw(long amount,WithdrawType type) throws InterruptedException {
        lock.lock();
        System.out.println("Withdraw Request of $"+amount+" from  Account "+accountNumber);
        try {
            //Preferred withdraw request
            if(type == WithdrawType.PREFERRED){
                preferredWithdrawCount ++;
            }

            while(((balance - amount) < 0) || (type == WithdrawType.ORDINARY && preferredWithdrawCount > 0)){
                //Either low balance or there is request for preferred withdraw
                if(type == WithdrawType.ORDINARY){
                    ordinaryWithdraw.await();
                }
                //Preferred withdraw but balance low
                else if(type == WithdrawType.PREFERRED) {
                    preferredWithdraw.await();
                }
            }

            balance -= amount;//Deduct amount from balance

            //Preferred withdraw complete
            if(type == WithdrawType.PREFERRED){
                preferredWithdrawCount --;
            }
            if(preferredWithdrawCount == 0){
                ordinaryWithdraw.signalAll(); // Now start with Ordinary Withdraws
            }
            System.out.println("Withdrawn $"+amount+" from  Account "+accountNumber+". New balance is $"+balance);
            return balance;
        }finally {
            lock.unlock();
        }
    }

    //Wrapper for Withdraw method:default set to the ordinary withdraw
    public long withdraw(long amount) throws InterruptedException {
        return withdraw(amount,WithdrawType.ORDINARY);
    }

    //Accept account from which balance to be deducted and adds to self
    public void transfer(int amount, Account reserve) throws InterruptedException {
            //Lock should not be taken, internally both of the following method acquires lock
            //Do not manipulate shared resources directly
            reserve.withdraw(amount);
            deposit(amount);
    }
}

//Thread for operating on account
class AccountHandler extends Thread{
    private Thread thread;
    private String threadName;
    private int myAccountNumber;//Account index handled by this thread
    static Account[] accountArray = new Account[10]; //Array to store accounts across all threads

    AccountHandler(int accountNumber){
        this.myAccountNumber = accountNumber;
        this.threadName = "Account_"+accountNumber;
    }
    public void run(){
        //Randomly choose account from whihch money to be transferred
        //Can be sent from main thread as well: On thread creation
        int targetAccountNumber = (int) (10 * Math.random());
        //Making sure not withdrawing from self
        while(targetAccountNumber == myAccountNumber){
            targetAccountNumber = (int) (10 * Math.random());
        }
        try {
            System.out.println("Transfer From Account "+targetAccountNumber+" to "+myAccountNumber);
            //Transfer amount of $100 from randomly chosen account to self
            //Need not tell class of accountArray, but just following convention
            AccountHandler.accountArray[myAccountNumber].transfer(100,AccountHandler.accountArray[targetAccountNumber]);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void start(){
        if(thread==null){
            thread = new Thread(this,threadName);
            thread.start();
        }
    }
}

public class hw2 {
    public static void main(String[] args) throws InterruptedException {
        AccountHandler[] accountThreads = new AccountHandler[10]; //Array to hold all threads
        int itr =0;
        //Generate all accounts in advance, We might receive withdraw request first and then handler might have control
        //Can do it on thread start but have to wait if other thread has not yet started
        //Print Initial balance from all accounts
        System.out.println("Account Number\tBalance");
        for(itr=0;itr<10;itr++) {
            //Adding random balance upto $300
            int amount = (int) (301 * Math.random());//300 is the max possible Value
            System.out.println("\t"+itr+"\t\t\t$"+amount);
            AccountHandler.accountArray[itr] = new Account(itr,amount);
        }

        //Start Accounts Handler Threads to Initiate Transfer
        for(itr=0;itr<10;itr++){
            AccountHandler thread = new AccountHandler(itr);
            thread.start();
            accountThreads[itr] = thread;
        }
        //It is 1pm, main thread will sleep until 2pm
        Thread.sleep(1000);

        //Boss deposited $1000 in all accounts
        for(itr=0;itr<10;itr++){
            AccountHandler.accountArray[itr].deposit(1000);
        }

        //Make sure all threads are finished executing
        for(itr=0;itr<10;itr++){
            accountThreads[itr].join();
        }

        //Making sure of the prints and operations
        Thread.sleep(100);

        //Finally, Printing balance of all accounts
        System.out.println("Account Number\tBalance");
        for(itr=0;itr<10;itr++){
            long balance = AccountHandler.accountArray[itr].getBalance();
            System.out.println("\t"+itr+"\t\t\t$"+balance);
        }

    }
}
