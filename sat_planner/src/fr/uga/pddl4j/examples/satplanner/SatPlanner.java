package fr.uga.pddl4j.examples.satplanner;

import fr.uga.pddl4j.heuristics.state.StateHeuristic;
import fr.uga.pddl4j.parser.DefaultParsedProblem;
import fr.uga.pddl4j.parser.RequireKey;
import fr.uga.pddl4j.plan.Plan;
import fr.uga.pddl4j.plan.SequentialPlan;
import fr.uga.pddl4j.planners.AbstractPlanner;
import fr.uga.pddl4j.planners.Planner;
import fr.uga.pddl4j.planners.PlannerConfiguration;
import fr.uga.pddl4j.planners.ProblemNotSupportedException;
import fr.uga.pddl4j.planners.SearchStrategy;
import fr.uga.pddl4j.planners.statespace.search.StateSpaceSearch;
import fr.uga.pddl4j.problem.DefaultProblem;
import fr.uga.pddl4j.problem.Problem;
import fr.uga.pddl4j.problem.State;
import fr.uga.pddl4j.problem.operator.Action;
import fr.uga.pddl4j.problem.operator.Condition;
import fr.uga.pddl4j.problem.operator.ConditionalEffect;
import fr.uga.pddl4j.problem.operator.Effect;
import fr.uga.pddl4j.problem.InitialState;
import fr.uga.pddl4j.problem.Fluent;
import fr.uga.pddl4j.util.BitVector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import org.sat4j.specs.*;
import org.sat4j.minisat.*;
import org.sat4j.core.Vec;
import org.sat4j.core.VecInt;



/**
 * The class is an example. It shows how to create a simple A* search planner able to
 * solve an ADL problem by choosing the heuristic to used and its weight.
 *
 * @author D. Pellier
 * @version 4.0 - 30.11.2021
 */
@CommandLine.Command(name = "SatPlanner",
    version = "SatPlanner 1.0",
    description = "Solves a specified planning problem using Sat4j library instead of implementing a solver from scratch",
    sortOptions = false,
    mixinStandardHelpOptions = true,
    headerHeading = "Usage:%n",
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    parameterListHeading = "%nParameters:%n",
    optionListHeading = "%nOptions:%n")
public class SatPlanner extends AbstractPlanner {

    /**
     * The class logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(SatPlanner.class.getName());

    /**
     * Instantiates the planning problem from a parsed problem.
     *
     * @param problem the problem to instantiate.
     * @return the instantiated planning problem or null if the problem cannot be instantiated.
     */
    @Override
    public boolean isSupported(Problem problem) {
        return true;
    }

    @Override
    public Problem instantiate(DefaultParsedProblem problem) {
        final Problem pb = new DefaultProblem(problem);
        pb.instantiate();
        return pb;
    }
    /**
     * Encode problem into a satisfiablitity problem and returning it as
     * a set of clauses. That will be solved by sat4j
     * @param problem
     * @return
    */
    public IVec<IVecInt> encode(final Problem problem, int steps){
        
        IVec<IVecInt> clauses = new Vec<IVecInt>();
        //Initial state encoding step = 0:
        BitVector positiveFluents = problem.getInitialState().getPositiveFluents();
        int nbFluents = problem.getFluents().size();
        for (int i = 0; i < nbFluents; i++) {
            int fluentUniqueNumber = i+1; // step0 * nbFluents + i + 1;
            if (positiveFluents.get(i)) {
                clauses.push(new VecInt( new int[]{fluentUniqueNumber} ));
            }else{
                clauses.push(new VecInt( new int[]{-fluentUniqueNumber} ));
            }
        }

        //goal state encoding step = N
        BitVector goalFluents = problem.getGoal().getPositiveFluents();
        for (int i = 0; i < nbFluents; i++) {
            if (goalFluents.get(i)) {
                int goalFluentUniqueNumber = steps*nbFluents + i + 1;
                clauses.push(new VecInt( new int[]{goalFluentUniqueNumber} ));
            }
        }

        List<Action> actions = problem.getActions();
        int nbAction = actions.size();
        for (int step = 0; step < steps; step++) {
            //actions encoding for each step : 0 -> N-1
            for (int actionIndex=0; actionIndex < nbAction; actionIndex++) {

                Action action = actions.get(actionIndex);
                int ai = (steps+1) * nbFluents + step * nbAction + actionIndex + 1; 
                
                BitVector preconditonFluents = action.getPrecondition().getPositiveFluents();
                for (int i = 0; i < nbFluents; i++) {
                    if (preconditonFluents.get(i)) {
                        int preconditionUniqueNumber = step * nbFluents + i + 1;
                        clauses.push(new VecInt( new int[]{-ai,preconditionUniqueNumber} ));
                    }
                }
                
                List<ConditionalEffect> effects = action.getConditionalEffects();
                for (int i = 0; i < effects.size(); i++) {
                    BitVector posEffectFluents = effects.get(i).getEffect().getPositiveFluents();
                    BitVector negEffectFluents = effects.get(i).getEffect().getNegativeFluents();
                    for (int j = 0; j < nbFluents; j++) {
                        int uniqueEffectNumber = nbFluents * (step+1) + j + 1;
                        if (posEffectFluents.get(j)) {
                            clauses.push(new VecInt( new int[]{-ai,uniqueEffectNumber} ));
                        }else if(negEffectFluents.get(j)){
                            clauses.push(new VecInt( new int[]{-ai,-uniqueEffectNumber} ));
                        }
                    }
                }

            }
            //Action disjunction encoding for each step : 0 -> N-1
            for (int i = 0; i < nbAction; i++) {
                int ai = (steps+1) * nbFluents + step * nbAction + i + 1;
                for (int j = i+1; j < nbAction; j++) {
                    int aj = (steps+1) * nbFluents + step * nbAction + j + 1;
                    clauses.push(new VecInt(new int[]{-ai, -aj}));
                }
            }

            //Transition encoding for each step : 0 -> N-1
            List<Fluent> fluents = problem.getFluents();
            for (int i = 0; i < fluents.size(); i++) {
                int fi = step * nbFluents + i + 1;
                int fi_1 = (step+1) * nbFluents + i + 1;

                // f becomes true → at least one action with f as positive effect was executed
                // clause : fi ∨ ¬fi_1 ∨ (a1 ∨ a2 ∨ ...)
                IVecInt posClause = new VecInt();
                posClause.push(fi);
                posClause.push(-fi_1);

                // f becomes false → at least one action with f as negative effect was executed
                // clause : ¬fi ∨ fi_1 ∨ (a1 ∨ a2 ∨ ...)
                IVecInt negClause = new VecInt();
                negClause.push(-fi);
                negClause.push(fi_1);


                for (int actionIndex = 0; actionIndex < nbAction; actionIndex++) {
                    Action action = actions.get(actionIndex);
                    int ai = (steps+1) * nbFluents + step * nbAction + actionIndex + 1;
                    List<ConditionalEffect> effects = action.getConditionalEffects();
                    for (int j = 0; j < effects.size(); j++) {
                        BitVector posFluentsEffectA = effects.get(j).getEffect().getPositiveFluents();
                        BitVector negFluentsEffectA = effects.get(j).getEffect().getNegativeFluents();
                        if (posFluentsEffectA.get(i)) {
                            posClause.push(ai);
                        }
                        if (negFluentsEffectA.get(i)) {
                            negClause.push(ai);
                        }
                    }
                }

                clauses.push(posClause);
                clauses.push(negClause);
            }
            
        }

        return clauses;
    }

    public Plan decode(Problem problem, int steps, int[] model){
        //int ai = (steps+1) * nbFluents + step * nbAction + actionIndex + 1;
        Plan plan = new SequentialPlan();
        int nbFluents = problem.getFluents().size();
        int nbAction  = problem.getActions().size();
        List<Integer> ai_s = new ArrayList<>();
        for (int i = 0; i < model.length; i++) {
            if (model[i] > (steps+1)*nbFluents ) {
                ai_s.add(model[i]);
            }
        }
        int k = 0;
        for (int step = 0; step < steps; step++) {
            for (int ai : ai_s) {
                int actionIndex = ai - ((steps+1) * nbFluents + step * nbAction + 1);
                if (actionIndex >= 0 && actionIndex < nbAction) {
                    plan.add(k, problem.getActions().get(actionIndex));
                    k++;
                }
            }
        }
        return plan;
    }

    /**
     * Search a solution plan to a specified domain and problem using A*.
     * We assume that the problem took from the input is not encoded. Thus
     * we need to encode the problem into a satisfiability problem.
     *
     * @param problem the problem to solve.
     * @return the plan found or null if no plan was found.
     */
    @Override
    public Plan solve(final Problem problem) {

        int steps = 2;
        IVec<IVecInt> clauses = encode(problem,steps);
        ISolver solver = SolverFactory.newDefault();
        try {
            solver.addAllClauses(clauses);
            int[] model = solver.findModel();
            Plan plan = decode(problem, steps, model);
            return plan;
        } catch (ContradictionException e) {
            System.out.println(e);
            return null;
        }catch (TimeoutException e){
            System.err.println(e);
            return null;
        } 
    }

    /**
     * The main method of the <code>SatPlanner</code> planner.
     *
     * @param args the arguments of the command line.
     */
    public static void main(String[] args) {
        try {
            final SatPlanner planner = new SatPlanner();
            CommandLine cmd = new CommandLine(planner);
            cmd.execute(args);
        } catch (IllegalArgumentException e) {
            LOGGER.fatal(e.getMessage());
        }
    }
}