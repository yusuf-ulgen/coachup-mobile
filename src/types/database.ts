export type Gender = 'male' | 'female';
export type Role = 'member' | 'coach' | 'admin' | 'guardian';

export interface UserProfile {
  id: string;
  email?: string | null;
  name?: string | null;
  surname?: string | null;
  phone?: string | null;
  birth_date?: string | null;
  gender?: string | null;
  role?: Role | string | null;
  avatar_url?: string | null;
  gym_id?: string | null;
  created_at?: string | null;
  updated_at?: string | null;
  membership_type?: string | null;
  membership_expiry?: string | null;
}

export interface TrainingProgram {
  id: string;
  gym_id: string;
  title: string;
  description?: string | null;
  difficulty?: 'beginner' | 'intermediate' | 'advanced' | string | null;
  duration_weeks?: number | null;
  created_by?: string | null;
  created_at?: string | null;
}

export interface WorkoutSession {
  id: string;
  user_id: string;
  program_id?: string | null;
  started_at: string;
  ended_at?: string | null;
  status: 'scheduled' | 'in_progress' | 'completed' | 'cancelled' | string;
  notes?: string | null;
  total_calories?: number | null;
}

export interface CommunityPost {
  id: string;
  gym_id: string;
  user_id: string;
  content: string;
  image_url?: string | null;
  likes_count?: number;
  comments_count?: number;
  created_at: string;
  author?: UserProfile;
}

export interface CoachInfo {
  id: string;
  user_id: string;
  gym_id: string;
  specialties?: string[] | null;
  bio?: string | null;
  rating?: number | null;
  user?: UserProfile;
}
