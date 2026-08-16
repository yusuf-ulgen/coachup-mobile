import React, { useState, useEffect, useRef } from 'react';
import { 
  View, Text, StyleSheet, TextInput, TouchableOpacity, 
  FlatList, KeyboardAvoidingView, Platform, ActivityIndicator
} from 'react-native';
import { ArrowLeft, Send, Check, CheckCheck } from 'lucide-react-native';
import { Colors } from '../../theme/colors';
import { supabase } from '../../services/supabaseClient';
import { useAuth } from '../../context/AuthContext';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export const CoachChatScreen: React.FC<any> = ({ route, navigation }) => {
  const insets = useSafeAreaInsets();
  const { coachId, coachName } = route.params;
  const { session } = useAuth();
  const [messages, setMessages] = useState<any[]>([]);
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(true);
  const flatListRef = useRef<FlatList>(null);

  const currentUserId = session?.user?.id;

  useEffect(() => {
    if (!currentUserId || !coachId) return;

    fetchMessages();
    
    // Subscribe to real-time coach_messages
    const channel = supabase
      .channel(`coach_messages:${currentUserId}:${coachId}`)
      .on('postgres_changes', {
        event: 'INSERT',
        schema: 'public',
        table: 'coach_messages',
        filter: `user_id=eq.${currentUserId}`,
      }, (payload) => {
        if (payload.new && payload.new.coach_id === coachId) {
          setMessages(prev => {
            if (prev.some(m => m.id === payload.new.id)) return prev;
            return [payload.new, ...prev];
          });
        }
      })
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [currentUserId, coachId]);

  const fetchMessages = async () => {
    if (!currentUserId || !coachId) return;
    setLoading(true);
    try {
      const { data, error } = await supabase
        .from('coach_messages')
        .select('*')
        .eq('user_id', currentUserId)
        .eq('coach_id', coachId)
        .order('created_at', { ascending: false });

      if (error) {
        console.error('Error fetching coach messages:', error);
        setMessages([]);
      } else {
        setMessages(data || []);
      }
    } catch (e) {
      console.error('Error in fetchMessages:', e);
      setMessages([]);
    } finally {
      setLoading(false);
    }
  };

  const sendMessage = async () => {
    if (!inputText.trim() || !currentUserId || !coachId) return;

    const messageText = inputText.trim();
    setInputText('');

    try {
      const { data, error } = await supabase
        .from('coach_messages')
        .insert({
          user_id: currentUserId,
          coach_id: coachId,
          message: messageText,
          sender_type: 'user',
          is_read: false,
          created_at: new Date().toISOString(),
        })
        .select()
        .single();

      if (error) throw error;

      setMessages(prev => [data, ...prev]);
    } catch (err: any) {
      console.error('Error sending message:', err);
    }
  };

  return (
    <KeyboardAvoidingView 
      style={styles.container} 
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 0}
    >
      <View style={[styles.header, { paddingTop: Math.max(16, insets.top + 8) }]}>
        <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backButton}>
          <ArrowLeft size={24} color={Colors.textDark} />
        </TouchableOpacity>
        <View style={styles.headerInfo}>
          <Text style={styles.headerTitle}>{coachName || 'Eğitmen'}</Text>
          <Text style={styles.onlineStatus}>Çevrimiçi</Text>
        </View>
      </View>

      {loading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={Colors.primary} />
        </View>
      ) : messages.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyTitle}>Henüz Mesaj Yok</Text>
          <Text style={styles.emptySubtitle}>Eğitmeninize bir soru sorarak sohbete başlayabilirsiniz.</Text>
        </View>
      ) : (
        <FlatList
          ref={flatListRef}
          data={messages}
          keyExtractor={(item) => item.id}
          inverted
          contentContainerStyle={styles.messagesList}
          renderItem={({ item }) => {
            const isMe = item.sender_type === 'user';
            return (
              <View style={[styles.messageBubble, isMe ? styles.myMessage : styles.theirMessage]}>
                <Text style={[styles.messageText, isMe ? styles.myMessageText : styles.theirMessageText]}>
                  {item.message}
                </Text>
                <View style={styles.messageFooter}>
                  <Text style={[styles.messageTime, isMe ? styles.myMessageTime : styles.theirMessageTime]}>
                    {new Date(item.created_at).toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' })}
                  </Text>
                  {isMe && (
                    item.is_read ? (
                      <CheckCheck size={14} color="#60A5FA" style={{ marginLeft: 4 }} />
                    ) : (
                      <Check size={14} color="rgba(255,255,255,0.7)" style={{ marginLeft: 4 }} />
                    )
                  )}
                </View>
              </View>
            );
          }}
        />
      )}

      <View style={[styles.inputContainer, { paddingBottom: Math.max(12, insets.bottom + 8) }]}>
        <TextInput
          style={styles.input}
          placeholder="Mesajınızı yazın..."
          placeholderTextColor="#64748B"
          value={inputText}
          onChangeText={setInputText}
          multiline
        />
        <TouchableOpacity 
          style={[styles.sendButton, !inputText.trim() && styles.sendButtonDisabled]}
          onPress={sendMessage}
          disabled={!inputText.trim()}
        >
          <Send size={18} color={Colors.allWhite} />
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0F172A',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingBottom: 12,
    backgroundColor: '#1E293B',
    borderBottomWidth: 1,
    borderBottomColor: '#334155',
  },
  backButton: {
    padding: 4,
    marginRight: 12,
  },
  headerInfo: {
    flex: 1,
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: Colors.textDark,
  },
  onlineStatus: {
    fontSize: 12,
    color: '#10B981',
    fontWeight: '500',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 32,
    gap: 8,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Colors.textDark,
  },
  emptySubtitle: {
    fontSize: 13,
    color: Colors.textSecondaryDark,
    textAlign: 'center',
    lineHeight: 18,
  },
  messagesList: {
    padding: 16,
    gap: 8,
  },
  messageBubble: {
    maxWidth: '80%',
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 16,
    marginBottom: 6,
  },
  myMessage: {
    alignSelf: 'flex-end',
    backgroundColor: Colors.primary,
    borderBottomRightRadius: 4,
  },
  theirMessage: {
    alignSelf: 'flex-start',
    backgroundColor: '#1E293B',
    borderBottomLeftRadius: 4,
    borderWidth: 1,
    borderColor: '#334155',
  },
  messageText: {
    fontSize: 14,
    lineHeight: 20,
  },
  myMessageText: {
    color: '#FFFFFF',
  },
  theirMessageText: {
    color: Colors.textDark,
  },
  messageFooter: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    marginTop: 4,
  },
  messageTime: {
    fontSize: 10,
  },
  myMessageTime: {
    color: 'rgba(255,255,255,0.7)',
  },
  theirMessageTime: {
    color: Colors.textSecondaryDark,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 10,
    backgroundColor: '#1E293B',
    borderTopWidth: 1,
    borderTopColor: '#334155',
    gap: 10,
  },
  input: {
    flex: 1,
    minHeight: 40,
    maxHeight: 100,
    backgroundColor: '#0F172A',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 8,
    color: Colors.textDark,
    fontSize: 14,
    borderWidth: 1,
    borderColor: '#334155',
  },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: Colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  sendButtonDisabled: {
    opacity: 0.5,
  },
});
