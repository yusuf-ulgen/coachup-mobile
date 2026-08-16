import { supabase } from './supabaseClient';

export interface SurveyQuestion {
  id: string;
  question: string;
  type: 'multiple_choice' | 'text' | 'rating' | 'yes_no';
  options?: string[];
}

export interface SurveyItem {
  id: string;
  gym_id?: string;
  title: string;
  description?: string;
  category?: string;
  questions?: SurveyQuestion[] | any;
  status: string;
  created_at: string;
  has_responded?: boolean;
}

export const SurveyService = {
  async fetchSurveys(userId: string, gymId?: string | null): Promise<SurveyItem[]> {
    try {
      // 1. Aktif anketleri çek
      let query = supabase
        .from('surveys')
        .select('*')
        .eq('status', 'active')
        .order('created_at', { ascending: false });

      if (gymId) {
        query = query.or(`gym_id.eq.${gymId},gym_id.is.null`);
      }

      const { data: surveys, error: surveyErr } = await query;
      if (surveyErr || !surveys) {
        console.error('Error fetching surveys:', surveyErr);
        return [];
      }

      // 2. Kullanıcının yanıtladığı anketleri çek
      const { data: responses } = await supabase
        .from('survey_responses')
        .select('survey_id')
        .eq('user_id', userId);

      const respondedSet = new Set((responses || []).map((r: any) => r.survey_id));

      return surveys.map((s: any) => {
        let questions = s.questions;
        if (!questions || (Array.isArray(questions) && questions.length === 0)) {
          if (s.question || s.title) {
            questions = [
              {
                id: 'q_1',
                question: s.question || s.title,
                type: 'rating',
              },
            ];
          } else {
            questions = [];
          }
        }
        return {
          ...s,
          questions,
          has_responded: respondedSet.has(s.id),
        };
      });
    } catch (e) {
      console.error('Error in SurveyService.fetchSurveys:', e);
      return [];
    }
  },

  async submitSurveyResponse(
    userId: string,
    surveyId: string,
    answers: Record<string, any>
  ): Promise<void> {
    const { error } = await supabase.from('survey_responses').insert({
      user_id: userId,
      survey_id: surveyId,
      answers,
      created_at: new Date().toISOString(),
    });

    if (error) {
      throw new Error(error.message || 'Anket yanıtı kaydedilemedi.');
    }
  },
};
