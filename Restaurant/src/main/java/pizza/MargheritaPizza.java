package pizza;


import com.stefanolupo.annotations.Factory;

@Factory(id = "Margherita", type=Meal.class)
public class MargheritaPizza implements Meal {

    @Override
    public int getPrice() {
        return 99;
    }
}
