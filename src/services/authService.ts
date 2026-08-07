import { supabase } from './supabaseClient';
import { GYM_CONFIG } from '../config/gym';

export const AuthService = {
  async signUp(
    email: string,
    password: string,
    name: string,
    gender: string,
    isIndividual: boolean = true,
    gymId: string = GYM_CONFIG.GYM_ID
  ) {
    if (!email || !email.includes('@')) {
      throw new Error('Geçersiz email adresi');
    }
    if (password.length < 6) {
      throw new Error('Şifre en az 6 karakter olmalıdır');
    }

    const { data, error } = await supabase.auth.signUp({
      email,
      password,
      options: {
        data: {
          name,
          gender,
          account_type: isIndividual ? 'individual' : 'gym',
          role: isIndividual ? 'individual' : 'member',
          gym_id: gymId,
          gym_name: GYM_CONFIG.GYM_NAME,
          default_location: GYM_CONFIG.DEFAULT_LOCATION,
        },
      },
    });

    if (error) {
      throw new Error(`Kayıt başarısız: ${error.message}`);
    }

    return data;
  },

  async signIn(email: string, password: string) {
    if (!email || !email.includes('@')) {
      throw new Error('Geçersiz email adresi');
    }
    if (!password) {
      throw new Error('Şifre boş olamaz');
    }

    const { data, error } = await supabase.auth.signInWithPassword({
      email,
      password,
    });

    if (error) {
      throw new Error(`Giriş başarısız: ${error.message}`);
    }

    return data;
  },

  async signOut() {
    const { error } = await supabase.auth.signOut();
    if (error) {
      throw new Error(`Çıkış başarısız: ${error.message}`);
    }
  },

  async ensureProfileFromAuthIfMissing(): Promise<void> {
    try {
      const { data: userData } = await supabase.auth.getUser();
      if (!userData?.user) return;
      const userId = userData.user.id;
      // Profil var mı kontrol et
      const { data: existing } = await supabase
        .from('users')
        .select('id')
        .eq('id', userId)
        .single();
      if (existing) return; // Zaten var
      // Yoksa oluştur
      const meta = userData.user.user_metadata || {};
      await supabase.from('users').upsert({
        id: userId,
        email: userData.user.email,
        name: meta.name || 'Kullanıcı',
        gender: meta.gender || null,
        role: meta.role || 'individual',
        gym_id: meta.gym_id || null,
        is_individual: meta.account_type === 'individual',
      });
    } catch (e) {
      console.error('ensureProfileFromAuthIfMissing failed:', e);
    }
  },

  async getCurrentUser() {
    const { data } = await supabase.auth.getUser();
    return data.user;
  },

  async getSession() {
    const { data } = await supabase.auth.getSession();
    return data.session;
  },

  async resetPasswordForEmail(email: string) {
    const { error } = await supabase.auth.resetPasswordForEmail(email);
    if (error) {
      throw new Error(`Şifre sıfırlama başarısız: ${error.message}`);
    }
  },

  async resendConfirmationEmail(email: string) {
    const { error } = await supabase.auth.resend({
      type: 'signup',
      email,
    });
    if (error) {
      throw new Error(`Doğrulama e-postası gönderilemedi: ${error.message}`);
    }
  },

  async getCurrentProfile() {
    const { data: userData } = await supabase.auth.getUser();
    if (!userData?.user) return null;
    const { data: profile } = await supabase
      .from('users')
      .select('*')
      .eq('id', userData.user.id)
      .single();

    if (profile) return profile;

    return {
      id: userData.user.id,
      email: userData.user.email,
      name: userData.user.user_metadata?.name || 'Kullanıcı',
      ...userData.user.user_metadata,
    };
  },
};
