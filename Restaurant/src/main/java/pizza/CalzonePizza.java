package pizza;

import com.stefanolupo.annotations.Factory;

@Factory(id = "Calzone", type = Meal.class)
public class CalzonePizza implements Meal {
    @Override
    public int getPrice() {
        return 10;
    }
}
