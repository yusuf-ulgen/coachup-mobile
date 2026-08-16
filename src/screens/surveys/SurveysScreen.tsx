import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput, ActivityIndicator } from 'react-native';
import { Colors } from '../../theme/colors';
import { useTheme } from '../../theme/ThemeContext';
import { Star, CheckCircle, FileText, X, ArrowLeft } from 'lucide-react-native';
import { feedback } from '../../services/feedbackService';
import { AuthService } from '../../services/authService';
import { UserService } from '../../services/userService';
import { SurveyService, SurveyItem } from '../../services/surveyService';
import { SmoothModal } from '../../components/motion/SmoothModal';
import { ScreenContainer } from '../../components/ScreenContainer';

export default function SurveysScreen({ navigation }: any) {
  const { colors } = useTheme();
  const [surveys, setSurveys] = useState<SurveyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedSurvey, setSelectedSurvey] = useState<SurveyItem | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [answers, setAnswers] = useState<Record<string, any>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    loadSurveys();
  }, []);

  const loadSurveys = async () => {
    setLoading(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) return;
      const profile = await UserService.fetchProfile(user.id);
      const gymId = await UserService.resolveActiveGymIdForContent(profile);
      const data = await SurveyService.fetchSurveys(user.id, gymId);
      setSurveys(data);
    } catch (e) {
      console.error('Error loading surveys:', e);
      feedback.error({ title: 'Hata', message: e, fallbackMessage: 'Anketler yüklenemedi.' });
    } finally {
      setLoading(false);
    }
  };

  const openSurvey = (survey: SurveyItem) => {
    if (survey.has_responded) {
      feedback.info({ title: 'Bilgi', message: 'Bu anketi zaten doldurdunuz.' });
      return;
    }
    setSelectedSurvey(survey);
    setAnswers({});
    setModalVisible(true);
  };

  const submitSurvey = async () => {
    if (!selectedSurvey) return;
    setSubmitting(true);
    try {
      const user = await AuthService.getCurrentUser();
      if (!user) throw new Error('Kullanıcı oturumu bulunamadı.');

      await SurveyService.submitSurveyResponse(user.id, selectedSurvey.id, answers);

      setSurveys((prev) =>
        prev.map((s) => (s.id === selectedSurvey.id ? { ...s, has_responded: true } : s))
      );
      setModalVisible(false);
      feedback.success({ title: 'Başarılı', message: 'Anket yanıtınız başarıyla kaydedildi. Teşekkürler!' });
    } catch (error: any) {
      feedback.error({ title: 'Hata', message: error, fallbackMessage: 'Anket gönderilemedi.' });
    } finally {
      setSubmitting(false);
    }
  };

  const getQuestionsList = (survey: SurveyItem) => {
    if (!survey.questions) return [];
    if (Array.isArray(survey.questions)) return survey.questions;
    try {
      return JSON.parse(survey.questions);
    } catch {
      return [];
    }
  };

  const renderQuestion = (q: any, index: number) => {
    const qId = q.id || `q_${index}`;
    const qTitle = q.question || q.text || `Soru ${index + 1}`;
    const qType = q.type || 'text';

    switch (qType) {
      case 'rating':
        return (
          <View key={qId} style={styles.questionContainer}>
            <Text style={[styles.questionText, { color: colors.textPrimary }]}>{qTitle}</Text>
            <View style={styles.ratingContainer}>
              {[1, 2, 3, 4, 5].map((star) => (
                <TouchableOpacity key={star} onPress={() => setAnswers({ ...answers, [qId]: star })}>
                  <Star
                    size={32}
                    color={Colors.warning}
                    fill={answers[qId] >= star ? Colors.warning : 'transparent'}
                  />
                </TouchableOpacity>
              ))}
            </View>
          </View>
        );
      case 'multiple_choice':
        const options = q.options || ['Evet', 'Hayır'];
        return (
          <View key={qId} style={styles.questionContainer}>
            <Text style={[styles.questionText, { color: colors.textPrimary }]}>{qTitle}</Text>
            {options.map((opt: string) => (
              <TouchableOpacity
                key={opt}
                style={[
                  styles.optionButton,
                  { borderColor: colors.border, backgroundColor: colors.cardBg },
                  answers[qId] === opt && styles.optionSelected,
                ]}
                onPress={() => setAnswers({ ...answers, [qId]: opt })}
              >
                <Text
                  style={[
                    styles.optionText,
                    { color: colors.textPrimary },
                    answers[qId] === opt && styles.optionTextSelected,
                  ]}
                >
                  {opt}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        );
      default:
        return (
          <View key={qId} style={styles.questionContainer}>
            <Text style={[styles.questionText, { color: colors.textPrimary }]}>{qTitle}</Text>
            <TextInput
              style={[
                styles.textInput,
                { borderColor: colors.border, color: colors.textPrimary, backgroundColor: colors.cardBg },
              ]}
              placeholder="Cevabınızı buraya yazın..."
              placeholderTextColor={colors.textSecondary}
              multiline
              numberOfLines={3}
              value={answers[qId] || ''}
              onChangeText={(text) => setAnswers({ ...answers, [qId]: text })}
            />
          </View>
        );
    }
  };

  return (
    <ScreenContainer includeTopInset={true} includeBottomInset={true}>
      <View style={[styles.container, { backgroundColor: colors.bg }]}>
        {/* Header */}
        <View style={[styles.header, { borderBottomColor: colors.border }]}>
          <TouchableOpacity onPress={() => navigation?.goBack()} style={styles.backBtn}>
            <ArrowLeft size={22} color={colors.textPrimary} />
          </TouchableOpacity>
          <Text style={[styles.headerTitle, { color: colors.textPrimary }]}>Anketler</Text>
          <View style={{ width: 24 }} />
        </View>

        {loading ? (
          <View style={styles.centerBox}>
            <ActivityIndicator size="large" color={Colors.primary} />
          </View>
        ) : (
          <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
            {surveys.length === 0 ? (
              <View style={styles.emptyState}>
                <FileText size={48} color={colors.textSecondary} />
                <Text style={[styles.emptyTitle, { color: colors.textPrimary }]}>Aktif Anket Bulunmuyor</Text>
                <Text style={[styles.emptySubtitle, { color: colors.textSecondary }]}>
                  Salonunuz tarafından yayınlanan yeni anketler burada listelenecektir.
                </Text>
              </View>
            ) : (
              surveys.map((survey) => {
                const qList = getQuestionsList(survey);
                return (
                  <TouchableOpacity
                    key={survey.id}
                    style={[
                      styles.surveyCard,
                      { backgroundColor: colors.cardBg, borderColor: colors.border },
                      survey.has_responded && styles.answeredCard,
                    ]}
                    onPress={() => openSurvey(survey)}
                    activeOpacity={0.8}
                  >
                    <View style={styles.cardHeader}>
                      <View style={{ flex: 1 }}>
                        <Text style={[styles.surveyTitle, { color: colors.textPrimary }]}>{survey.title}</Text>
                        {survey.description ? (
                          <Text style={[styles.surveyDesc, { color: colors.textSecondary }]}>
                            {survey.description}
                          </Text>
                        ) : null}
                      </View>
                      {survey.has_responded ? (
                        <View style={styles.answeredBadge}>
                          <CheckCircle size={16} color="#10B981" />
                          <Text style={styles.answeredText}>Tamamlandı</Text>
                        </View>
                      ) : (
                        <View style={styles.pendingBadge}>
                          <Text style={styles.pendingText}>Doldur</Text>
                        </View>
                      )}
                    </View>

                    <View style={styles.cardFooter}>
                      <Text style={[styles.metaText, { color: colors.textSecondary }]}>
                        {qList.length} Soru
                      </Text>
                      <Text style={[styles.metaText, { color: colors.textSecondary }]}>
                        {new Date(survey.created_at).toLocaleDateString('tr-TR')}
                      </Text>
                    </View>
                  </TouchableOpacity>
                );
              })
            )}
          </ScrollView>
        )}

        {/* Anket Doldurma Modalı */}
        <SmoothModal visible={modalVisible} onClose={() => setModalVisible(false)}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Text style={[styles.modalTitle, { color: colors.textPrimary }]}>
                {selectedSurvey?.title}
              </Text>
              <TouchableOpacity onPress={() => setModalVisible(false)} style={styles.closeBtn}>
                <X size={20} color={colors.textPrimary} />
              </TouchableOpacity>
            </View>

            <ScrollView style={styles.modalBody} showsVerticalScrollIndicator={false}>
              {selectedSurvey &&
                getQuestionsList(selectedSurvey).map((q: any, idx: number) => renderQuestion(q, idx))}
            </ScrollView>

            <View style={styles.modalFooter}>
              <TouchableOpacity
                style={[styles.submitButton, submitting && { opacity: 0.7 }]}
                onPress={submitSurvey}
                disabled={submitting}
              >
                {submitting ? (
                  <ActivityIndicator size="small" color="#FFFFFF" />
                ) : (
                  <Text style={styles.submitButtonText}>Yanıtları Gönder</Text>
                )}
              </TouchableOpacity>
            </View>
          </View>
        </SmoothModal>
      </View>
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  backBtn: {
    padding: 4,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '700',
  },
  content: {
    padding: 16,
    gap: 12,
  },
  centerBox: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  surveyCard: {
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    gap: 12,
  },
  answeredCard: {
    opacity: 0.8,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 10,
  },
  surveyTitle: {
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 4,
  },
  surveyDesc: {
    fontSize: 13,
    lineHeight: 18,
  },
  answeredBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  answeredText: {
    fontSize: 12,
    color: '#10B981',
    fontWeight: '600',
  },
  pendingBadge: {
    backgroundColor: Colors.primary,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
  },
  pendingText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '600',
  },
  cardFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.05)',
    paddingTop: 8,
  },
  metaText: {
    fontSize: 12,
  },
  emptyState: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 60,
    gap: 12,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '600',
  },
  emptySubtitle: {
    fontSize: 13,
    textAlign: 'center',
    paddingHorizontal: 32,
    lineHeight: 18,
  },
  modalContent: {
    maxHeight: '80%',
    padding: 16,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
    paddingBottom: 8,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.1)',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    flex: 1,
  },
  closeBtn: {
    padding: 4,
  },
  modalBody: {
    maxHeight: 400,
  },
  questionContainer: {
    marginBottom: 18,
  },
  questionText: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
  },
  ratingContainer: {
    flexDirection: 'row',
    gap: 8,
    justifyContent: 'center',
    paddingVertical: 8,
  },
  optionButton: {
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    marginBottom: 8,
  },
  optionSelected: {
    borderColor: Colors.primary,
    backgroundColor: 'rgba(255, 96, 71, 0.15)',
  },
  optionText: {
    fontSize: 14,
  },
  optionTextSelected: {
    color: Colors.primary,
    fontWeight: '600',
  },
  textInput: {
    borderWidth: 1,
    borderRadius: 8,
    padding: 10,
    fontSize: 14,
    textAlignVertical: 'top',
  },
  modalFooter: {
    marginTop: 16,
  },
  submitButton: {
    backgroundColor: Colors.primary,
    padding: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  submitButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700',
  },
});
