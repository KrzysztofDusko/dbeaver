package org.jkiss.dbeaver.ext.firebird.model.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jkiss.utils.Pair;

public class FireBirdPlanBuilder {
	
	private String plan;
	
	public FireBirdPlanBuilder(String plan) {
		super();
		this.plan = plan;
	}

	public List<FireBirdPlanNode> Build() {
		List<FireBirdPlanNode> rootNodes = new ArrayList<>();
		String [] plans = plan.split("\\n");
		for (String plan: plans) {
			PlanMaker pm = new PlanMaker(plan);
			FireBirdPlanNode node = pm.parse();
			rootNodes.add(node);
		}
		return rootNodes;
	}
	
	private class PlanMaker {

		private List<Matcher> matchers = new ArrayList<>(PlanToken.values().length);
		private Map<Matcher,PlanToken> matchertokens = new HashMap<>(PlanToken.values().length);
		private String plan;
		private int position = 0;
		
		PlanMaker(String plan) {
			this.plan = plan;
			for (PlanToken token: PlanToken.values()) {
				Matcher matcher = token.newMatcher(plan);
				matchers.add(matcher);
				matchertokens.put(matcher, token);
			}
		}
		
		public FireBirdPlanNode parse() {
			FireBirdPlanNode node = null;
			FireBirdPlanNode plannode = null;
			String identificator = "";
			Pair<PlanToken, String> token;
			while (position < plan.length()) {
				token = jump();
				switch (token.getFirst()) {
				case PLAN:
					//node = addPlanNode(node, token.getSecond() + "[" + plan + "]");
					node = addPlanNode(null, plan);
					plannode = node;
					break;
				case JOIN:
					node = addPlanNode(node, token.getSecond());
					//jump();
					break;
				case NATURAL:
					addPlanNode(node, identificator + " " + token.getSecond());
					identificator = "";
					break;
				case INDEX:
					String indexes = "";
					do {
						token = jump();
						indexes = indexes + token.getSecond();
					} while (token.getFirst() != PlanToken.RIGHTPARENTHESE);
					addPlanNode(node, identificator + " " + indexes);
					identificator = "";
					break;
				case RIGHTPARENTHESE:
					node = node.parent;
					break;
				case IDENTIFICATOR:
					identificator = identificator + " " + token.getSecond();
					break;
				default:
					break;
				}
			}
			return plannode;
		}
		
		private Pair<PlanToken, String> find() {
			for (Matcher matcher: matchers) {
				if (matcher.find(position)) {
					position = position + matcher.group().length();
					return new Pair<>(matchertokens.get(matcher), matcher.group());
				}
			}
			return new Pair<>(PlanToken.IDENTIFICATOR, "???");
		}
		
		private Pair<PlanToken, String> jump() {
			Pair<PlanToken, String> token = null;
			do {
				token = find();
			} while (token.getFirst() == PlanToken.WHITESPACE);
			return token;
		}
		
		private FireBirdPlanNode addPlanNode(FireBirdPlanNode parent, String text) {
			FireBirdPlanNode node;
			node = new FireBirdPlanNode(text);
			node.parent = parent;
			if (parent != null) {
				parent.getNested().add(node);
			}
			return node;
		}
	}
	
	private enum PlanToken {
		PLAN("\\GPLAN\\b"), 
		JOIN("\\GJOIN\\b"), 
		NATURAL("\\GNATURAL\\b"), 
		INDEX("\\GINDEX\\b"), 
		LEFTPARENTHESE("\\G\\("), 
		RIGHTPARENTHESE("\\G\\)"),
		COMMA("\\G,"),
		WHITESPACE("\\G\\s+"),
		IDENTIFICATOR("\\G\\b[\\w$]+\\b"),
		UNRECOGNIZED("\\G\\b[^\\s]+\\b");
	    
		private final Pattern pattern;

	    private PlanToken(String regex) {
	        pattern = Pattern.compile(regex);
	    }
	    
	    public Matcher newMatcher(String text) {
	    	Matcher matcher = pattern.matcher(text);
	        return matcher;
	    }
	}

}
