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
      const todayStr = new Date().toISOString().split('T')[0];

      // 1. Kullanıcının aktif beslenme plan atamasını çek (status = 'active' ve tarih aralığı)
      let query = supabase
        .from('user_nutrition_plans')
        .select('*, plan:nutrition_plans(*)')
        .eq('user_id', userId)
        .order('created_at', { ascending: false });

      const { data: assignments, error: assignErr } = await query;

      if (assignErr || !assignments || assignments.length === 0) {
        return null;
      }

      // Filter active assignment (status = 'active' or null, and valid date range if dates exist)
      const validAssignment = assignments.find((a: any) => {
        if (a.status && a.status !== 'active') return false;
        if (a.start_date && a.start_date > todayStr) return false;
        if (a.end_date && a.end_date < todayStr) return false;
        return !!a.plan;
      }) || assignments.find((a: any) => a.plan);

      if (!validAssignment || !validAssignment.plan) {
        return null;
      }

      const planData = validAssignment.plan;
      const targetCalories = planData.target_calories || planData.total_calories || 2000;

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
        target_calories: targetCalories,
        meals: fullMeals,
      };
    } catch (e) {
      console.error('Error in NutritionService.fetchActivePlanForUser:', e);
      return null;
    }
  },
};
