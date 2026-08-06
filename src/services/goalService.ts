import { supabase } from './supabaseClient';

export interface UserGoal {
  id: string;
  user_id: string;
  title: string;
  target_date?: string;
  progress_percentage: number;
  status: string;
  created_at?: string;
}

export const GoalService = {
  async fetchGoalsForUser(userId: string): Promise<UserGoal[]> {
    try {
      const { data, error } = await supabase
        .from('user_goals')
        .select('*')
        .eq('user_id', userId)
        .order('created_at', { ascending: false });

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching user goals:', e);
      return [];
    }
  },

  async fetchGoalsForDate(userId: string, dateStr: string): Promise<UserGoal[]> {
    try {
      const { data, error } = await supabase
        .from('user_goals')
        .select('*')
        .eq('user_id', userId)
        .or(`target_date.eq.${dateStr},created_at.gte.${dateStr}T00:00:00,created_at.lte.${dateStr}T23:59:59`);

      if (error) throw error;
      return data || [];
    } catch (e) {
      // Fallback: fetch user goals and filter locally if RPC/OR fails
      const all = await this.fetchGoalsForUser(userId);
      return all.filter(
        (g) => g.target_date === dateStr || g.created_at?.startsWith(dateStr)
      );
    }
  },

  async createGoal(userId: string, title: string, targetDate?: string) {
    const { data, error } = await supabase.from('user_goals').insert({
      user_id: userId,
      title,
      target_date: targetDate,
      progress_percentage: 0,
      status: 'in_progress',
      created_at: new Date().toISOString(),
    });

    if (error) throw error;
    return data;
  },

  async updateGoalProgress(goalId: string, progress: number, status: string = 'in_progress') {
    const { data, error } = await supabase
      .from('user_goals')
      .update({
        progress_percentage: progress,
        status: status,
      })
      .eq('id', goalId);

    if (error) throw error;
    return data;
  },
};
