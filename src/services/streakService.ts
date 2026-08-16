import { supabase } from './supabaseClient';
import { formatLocalDate } from '../utils/dateUtils';

export interface StreakDayInfo {
  day: string;
  dateStr: string;
  active: boolean;
}

export interface StreakRecentActivity {
  id: string;
  title: string;
  dateStr: string;
  relativeDate: string;
}

export interface StreakData {
  currentStreak: number;
  longestStreak: number;
  last7Days: StreakDayInfo[];
  recentActivities: StreakRecentActivity[];
}

export const StreakService = {
  async fetchStreakData(userId: string): Promise<StreakData> {
    try {
      // 1. Fetch all completed sessions
      const { data: sessions, error } = await supabase
        .from('training_sessions')
        .select('id, completed_at, program:training_programs(name), notes')
        .eq('user_id', userId)
        .not('completed_at', 'is', null)
        .order('completed_at', { ascending: false });

      if (error) throw error;

      // Extract unique local dates
      const completedDatesSet = new Set<string>();
      (sessions || []).forEach((s: any) => {
        if (s.completed_at) {
          const localDate = formatLocalDate(new Date(s.completed_at));
          completedDatesSet.add(localDate);
        }
      });

      const today = new Date();
      const todayStr = formatLocalDate(today);
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayStr = formatLocalDate(yesterday);

      // 2. Calculate current streak
      let currentStreak = 0;
      if (completedDatesSet.has(todayStr)) {
        currentStreak = 1;
        let checkDate = new Date(yesterday);
        while (completedDatesSet.has(formatLocalDate(checkDate))) {
          currentStreak++;
          checkDate.setDate(checkDate.getDate() - 1);
        }
      } else if (completedDatesSet.has(yesterdayStr)) {
        currentStreak = 1;
        let checkDate = new Date(yesterday);
        checkDate.setDate(checkDate.getDate() - 1);
        while (completedDatesSet.has(formatLocalDate(checkDate))) {
          currentStreak++;
          checkDate.setDate(checkDate.getDate() - 1);
        }
      } else {
        currentStreak = 0;
      }

      // 3. Calculate longest streak
      const sortedDates = Array.from(completedDatesSet).sort();
      let maxStreak = 0;
      let tempStreak = 0;
      let prevTimestamp = 0;

      for (const dStr of sortedDates) {
        const [y, m, d] = dStr.split('-').map(Number);
        const currTime = new Date(y, m - 1, d).getTime();
        const oneDayMs = 24 * 60 * 60 * 1000;

        if (prevTimestamp === 0) {
          tempStreak = 1;
        } else {
          const diffDays = Math.round((currTime - prevTimestamp) / oneDayMs);
          if (diffDays === 1) {
            tempStreak++;
          } else if (diffDays > 1) {
            tempStreak = 1;
          }
        }
        if (tempStreak > maxStreak) {
          maxStreak = tempStreak;
        }
        prevTimestamp = currTime;
      }

      const longestStreak = Math.max(currentStreak, maxStreak);

      // 4. Calculate last 7 days (Monday to Sunday of current week)
      const dayNames = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cts', 'Paz'];
      const now = new Date();
      const currentDayOfWeek = (now.getDay() + 6) % 7; // 0 for Monday, 6 for Sunday
      const monday = new Date(now);
      monday.setDate(now.getDate() - currentDayOfWeek);

      const last7Days: StreakDayInfo[] = [];
      for (let i = 0; i < 7; i++) {
        const dayDate = new Date(monday);
        dayDate.setDate(monday.getDate() + i);
        const dStr = formatLocalDate(dayDate);
        last7Days.push({
          day: dayNames[i],
          dateStr: dStr,
          active: completedDatesSet.has(dStr),
        });
      }

      // 5. Recent activities
      const recentActivities: StreakRecentActivity[] = (sessions || []).slice(0, 5).map((s: any) => {
        const sDate = s.completed_at ? new Date(s.completed_at) : new Date();
        const dStr = formatLocalDate(sDate);
        let relative = dStr;
        if (dStr === todayStr) {
          relative = 'Bugün';
        } else if (dStr === yesterdayStr) {
          relative = 'Dün';
        } else {
          const months = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];
          relative = `${sDate.getDate()} ${months[sDate.getMonth()]}`;
        }

        return {
          id: s.id,
          title: s.program?.name || 'Antrenman Oturumu',
          dateStr: dStr,
          relativeDate: relative,
        };
      });

      // 6. Sync current streak to users table in background
      supabase
        .from('users')
        .update({ current_streak: currentStreak })
        .eq('id', userId)
        .then(() => {});

      return {
        currentStreak,
        longestStreak,
        last7Days,
        recentActivities,
      };
    } catch (e) {
      console.error('Error fetching streak data:', e);
      return {
        currentStreak: 0,
        longestStreak: 0,
        last7Days: [
          { day: 'Pzt', dateStr: '', active: false },
          { day: 'Sal', dateStr: '', active: false },
          { day: 'Çar', dateStr: '', active: false },
          { day: 'Per', dateStr: '', active: false },
          { day: 'Cum', dateStr: '', active: false },
          { day: 'Cmt', dateStr: '', active: false },
          { day: 'Paz', dateStr: '', active: false },
        ],
        recentActivities: [],
      };
    }
  },
};
