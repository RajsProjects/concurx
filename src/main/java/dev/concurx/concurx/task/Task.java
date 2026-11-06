package dev.concurx.concurx.task;

public class Task implements  Runnable{
    private final String name;
    private final int id;

    public Task(String name, int id){
        this.name = name;
        this.id = id;
    }

    @Override
    public void run() {
        System.out.println("Task Id: " + this.id + "Task Name: " + this.name +
                "Thread : " + Thread.currentThread().getName());
        try{
            Thread.sleep(2000);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            System.out.println("Task Id: " + this.id + "Thread Interrupted");
        }
        System.out.println("task completed" + this.id);
    }
}
