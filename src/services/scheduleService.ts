import { supabase } from './supabaseClient';

export interface ScheduledProgram {
  id: string;
  user_id: string;
  program_id?: string;
  coach_id?: string;
  scheduled_date: string;
  start_time: string;
  end_time: string;
  status: string;
  program?: {
    id: string;
    name: string;
    description?: string;
    category?: string;
  };
  coach?: {
    id: string;
    name?: string;
    surname?: string;
  };
}

export const ScheduleService = {
  async fetchScheduledPrograms(userId: string, dateStr: string): Promise<ScheduledProgram[]> {
    try {
      const { data, error } = await supabase
        .from('scheduled_programs')
        .select('*, program:training_programs(*), coach:coaches(*)')
        .eq('user_id', userId)
        .eq('scheduled_date', dateStr)
        .order('start_time', { ascending: true });

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching scheduled programs:', e);
      return [];
    }
  },

  async fetchMonthPrograms(userId: string, month: number, year: number): Promise<ScheduledProgram[]> {
    try {
      const startDate = `${year}-${String(month).padStart(2, '0')}-01`;
      const endDate = `${year}-${String(month + 1).padStart(2, '0')}-01`;

      const { data, error } = await supabase
        .from('scheduled_programs')
        .select('*, program:training_programs(*), coach:coaches(*)')
        .eq('user_id', userId)
        .gte('scheduled_date', startDate)
        .lt('scheduled_date', endDate)
        .order('scheduled_date', { ascending: true });

      if (error) throw error;
      return data || [];
    } catch (e) {
      console.error('Error fetching month programs:', e);
      return [];
    }
  },

  async scheduleProgram(
    userId: string,
    programId: string,
    dateStr: string,
    startTime: string,
    endTime: string,
    coachId?: string
  ) {
    const { data, error } = await supabase.from('scheduled_programs').insert({
      user_id: userId,
      program_id: programId,
      coach_id: coachId,
      scheduled_date: dateStr,
      start_time: startTime,
      end_time: endTime,
      status: 'scheduled',
    });
    if (error) throw error;
    return data;
  },

  async cancelScheduledProgram(id: string) {
    const { error } = await supabase
      .from('scheduled_programs')
      .update({ status: 'cancelled' })
      .eq('id', id);
    if (error) throw error;
  },
};
