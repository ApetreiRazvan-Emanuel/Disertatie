package benchmark;

import gurobi.*;

/**
 * Quick test to verify Gurobi is correctly linked. Solves: maximize x + y, subject to x + 2y <= 4, x,y >= 0.
 * Expected solution: x=4, y=0, objective=4.
 */
public class GurobiTest {
    public static void main(String[] args) {
        try {
            GRBEnv env = new GRBEnv();
            GRBModel model = new GRBModel(env);

            GRBVar x = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "x");
            GRBVar y = model.addVar(0, GRB.INFINITY, 0, GRB.CONTINUOUS, "y");

            model.setObjective(new GRBLinExpr() {{ addTerm(1, x); addTerm(1, y); }}, GRB.MAXIMIZE);

            GRBLinExpr constraint = new GRBLinExpr();
            constraint.addTerm(1, x);
            constraint.addTerm(2, y);
            model.addConstr(constraint, GRB.LESS_EQUAL, 4, "c1");

            model.optimize();

            System.out.println("Status: " + model.get(GRB.IntAttr.Status));
            System.out.printf("x = %.1f, y = %.1f, obj = %.1f%n",
                    x.get(GRB.DoubleAttr.X), y.get(GRB.DoubleAttr.X), model.get(GRB.DoubleAttr.ObjVal));

            model.dispose();
            env.dispose();

            System.out.println("Gurobi is working!");
        } catch (GRBException e) {
            System.err.println("Gurobi error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
