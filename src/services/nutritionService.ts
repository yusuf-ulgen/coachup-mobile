import { supabase } from './supabaseClient';

export interface NutritionFood {
  id: string;
  meal_id: string;
  name: string;
  portion?: string;
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
  order_index: number;
}

export interface NutritionMeal {
  id: string;
  plan_id: string;
  name: string;
  meal_type: string;
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
  description?: string;
  order_index: number;
  foods?: NutritionFood[];
}

export interface NutritionPlan {
  id: string;
  gym_id?: string;
  name: string;
  description?: string;
  target_calories: number;
  meal_count: number;
  category?: string;
  meals: NutritionMeal[];
}

export const NutritionService = {
  async fetchActivePlanForUser(userId: string): Promise<NutritionPlan | null> {
    try {
      // 1. Kullanıcının aktif beslenme plan atamasını çek
      const { data: assignment, error: assignErr } = await supabase
        .from('user_nutrition_plans')
        .select('*, plan:nutrition_plans(*)')
        .eq('user_id', userId)
        .order('created_at', { ascending: false })
        .limit(1)
        .maybeSingle();

      if (assignErr || !assignment || !assignment.plan) {
        return null;
      }

      const planData = assignment.plan;

      // 2. Planın öğünlerini çek
      const { data: mealsData, error: mealsErr } = await supabase
        .from('nutrition_meals')
        .select('*')
        .eq('plan_id', planData.id)
        .order('order_index', { ascending: true });

      if (mealsErr || !mealsData) {
        return {
          ...planData,
          meals: [],
        };
      }

      const mealIds = mealsData.map((m) => m.id);

      // 3. Öğünlerin besinlerini çek
      let foodsByMeal: Record<string, NutritionFood[]> = {};
      if (mealIds.length > 0) {
        const { data: foodsData } = await supabase
          .from('nutrition_foods')
          .select('*')
          .in('meal_id', mealIds)
          .order('order_index', { ascending: true });

        (foodsData || []).forEach((food: any) => {
          if (!foodsByMeal[food.meal_id]) foodsByMeal[food.meal_id] = [];
          foodsByMeal[food.meal_id].push(food);
        });
      }

      const fullMeals: NutritionMeal[] = mealsData.map((m) => ({
        ...m,
        foods: foodsByMeal[m.id] || [],
      }));

      return {
        ...planData,
        meals: fullMeals,
      };
    } catch (e) {
      console.error('Error in NutritionService.fetchActivePlanForUser:', e);
      return null;
    }
  },
};
