package com.bilibili.video;

class ClassDemo {
    static float x;
    int y;

    ClassDemo() {
    }

    public static void main(String[] args) {
        System.out.println("Hello World!");
        System.out.println("x是类变量,可以直接用类名调用它 " + x);

        ClassDemo a = new ClassDemo();
        System.out.println("y是实例变量,必须通过对象a来调用 a.y " + a.y);

        Mydate mydate = new Mydate();

        mydate.setDay(0);
        System.out.println(mydate.getDay());

        mydate.setYear(2022);
        mydate.setMonth(5);

        System.out.println("Year: " + mydate.getYear());
        System.out.println("Month: " + mydate.getMonth());
    }
}

class Mydate {
    private int year;
    private int month;
    private int day;

    Mydate() {}

    // day
    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        if (day < 1) {
            System.out.println("日期不能小于1");
            return;
        } else if (day > 31) {
            System.out.println("日期不能大于31");
            return;
        }
        this.day = day;
    }

    // year
    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        if (year < 1900 || year > 2100) {
            System.out.println("年份不合法");
            return;
        }
        this.year = year;
    }

    // month
    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        if (month < 1 || month > 12) {
            System.out.println("月份不合法");
            return;
        }
        this.month = month;
    }
}