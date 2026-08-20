import React, { createContext, useContext, useEffect, useState } from 'react';
import { Session, User } from '@supabase/supabase-js';
import { supabase } from '../services/supabaseClient';
import { UserProfile } from '../types/database';

interface AuthContextType {
  user: User | null;
  profile: UserProfile | null;
  session: Session | null;
  loading: boolean;
  signOut: () => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  profile: null,
  session: null,
  loading: true,
  signOut: async () => {},
  refreshProfile: async () => {},
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const hydratedUserIdRef = React.useRef<string | null>(null);

  const fetchProfile = async (userId: string) => {
    try {
      const { data, error } = await supabase
        .from('users')
        .select('*')
        .eq('id', userId)
        .single();
      if (!error && data) {
        setProfile(data as UserProfile);
      }
    } catch (err) {
      console.warn('Profile fetch error:', err);
    }
  };

  useEffect(() => {
    // Get initial session safely
    supabase.auth
      .getSession()
      .then(async ({ data: { session } }) => {
        setSession(session);
        setUser(session?.user ?? null);
        if (session?.user?.id) {
          await fetchProfile(session.user.id);
          hydratedUserIdRef.current = session.user.id;
        } else {
          hydratedUserIdRef.current = null;
        }
      })
      .catch((err) => {
        console.warn('Initial session error:', err);
      })
      .finally(() => {
        setLoading(false);
      });

    // Listen for auth state changes safely
    const { data: { subscription } } = supabase.auth.onAuthStateChange(async (event, session) => {
      const nextUserId = session?.user?.id ?? null;

      if (event === 'SIGNED_OUT' || !nextUserId) {
        hydratedUserIdRef.current = null;
        setSession(null);
        setUser(null);
        setProfile(null);
        setLoading(false);
        return;
      }

      // If token refreshed for the already-hydrated user, update session without resetting loading state
      if (event === 'TOKEN_REFRESHED' && nextUserId === hydratedUserIdRef.current) {
        setSession(session);
        setUser(session?.user ?? null);
        return;
      }

      // New sign-in or identity change: gate with loading=true until profile is hydrated
      setLoading(true);
      try {
        setSession(session);
        setUser(session?.user ?? null);
        if (hydratedUserIdRef.current !== nextUserId) {
          setProfile(null);
        }
        await fetchProfile(nextUserId);
        hydratedUserIdRef.current = nextUserId;
      } catch (err) {
        console.warn('Auth state change error:', err);
        hydratedUserIdRef.current = nextUserId;
      } finally {
        setLoading(false);
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, []);

  const signOut = async () => {
    setLoading(true);
    hydratedUserIdRef.current = null;
    await supabase.auth.signOut();
    setUser(null);
    setSession(null);
    setProfile(null);
    setLoading(false);
  };

  const refreshProfile = async () => {
    if (user?.id) {
      await fetchProfile(user.id);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        profile,
        session,
        loading,
        signOut,
        refreshProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
