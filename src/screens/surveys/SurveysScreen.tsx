import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Modal, TextInput, Alert, SafeAreaView } from 'react-native';
import { Colors } from '../../theme/colors';
import { Star, CheckCircle, FileText, X, Calendar } from 'lucide-react-native';
import { supabase } from '../../services/supabaseClient';

const mockSurveys = [
  {
    id: '1',
    title: 'Aylık Gelişim Değerlendirmesi',
    questionCount: 5,
    endDate: '2023-12-01',
    answered: false,
    questions: [
      { id: 'q1', type: 'rating', text: 'Eğitmeninizden ne kadar memnunsunuz?' },
      { id: 'q2', type: 'multiple_choice', text: 'Hangi alanlarda gelişmek istiyorsunuz?', options: ['Kardiyo', 'Güç', 'Esneklik'] },
      { id: 'q3', type: 'text', text: 'Eklemek istedikleriniz?' },
    ],
  },
  {
    id: '2',
    title: 'Tesis Geri Bildirimi',
    questionCount: 3,
    endDate: '2023-11-20',
    answered: true,
    questions: [],
  },
];

export default function SurveysScreen() {
  const [surveys, setSurveys] = useState(mockSurveys);
  const [selectedSurvey, setSelectedSurvey] = useState<any>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [answers, setAnswers] = useState<Record<string, any>>({});

  const openSurvey = (survey: any) => {
    if (survey.answered) {
      Alert.alert('Bilgi', 'Bu anketi zaten doldurdunuz.');
      return;
    }
    setSelectedSurvey(survey);
    setAnswers({});
    setModalVisible(true);
  };

  const submitSurvey = async () => {
    try {
      /* Supabase kaydı (gerçek uygulamada)
      const { data, error } = await supabase
        .from('survey_responses')
        .insert([{ survey_id: selectedSurvey.id, answers }]);
      if (error) throw error;
      */

      setSurveys((prev) =>
        prev.map((s) => (s.id === selectedSurvey.id ? { ...s, answered: true } : s))
      );
      setModalVisible(false);
      Alert.alert('Başarılı', 'Anket başarıyla gönderildi.');
    } catch (error) {
      Alert.alert('Hata', 'Anket gönderilemedi.');
    }
  };

  const renderQuestion = (q: any) => {
    switch (q.type) {
      case 'rating':
        return (
          <View key={q.id} style={styles.questionContainer}>
            <Text style={styles.questionText}>{q.text}</Text>
            <View style={styles.ratingContainer}>
              {[1, 2, 3, 4, 5].map((star) => (
                <TouchableOpacity key={star} onPress={() => setAnswers({ ...answers, [q.id]: star })}>
                  <Star
                    size={32}
                    color={Colors.warning}
                    fill={answers[q.id] >= star ? Colors.warning : 'transparent'}
                  />
                </TouchableOpacity>
              ))}
            </View>
          </View>
        );
      case 'multiple_choice':
        return (
          <View key={q.id} style={styles.questionContainer}>
            <Text style={styles.questionText}>{q.text}</Text>
            {q.options.map((opt: string) => (
              <TouchableOpacity
                key={opt}
                style={[
                  styles.optionButton,
                  answers[q.id] === opt && styles.optionSelected,
                ]}
                onPress={() => setAnswers({ ...answers, [q.id]: opt })}
              >
                <Text
                  style={[
                    styles.optionText,
                    answers[q.id] === opt && styles.optionTextSelected,
                  ]}
                >
                  {opt}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        );
      case 'text':
        return (
          <View key={q.id} style={styles.questionContainer}>
            <Text style={styles.questionText}>{q.text}</Text>
            <TextInput
              style={styles.textInput}
              multiline
              numberOfLines={4}
              placeholder="Cevabınızı buraya yazın..."
              placeholderTextColor={Colors.textLight}
              value={answers[q.id] || ''}
              onChangeText={(text) => setAnswers({ ...answers, [q.id]: text })}
            />
          </View>
        );
      default:
        return null;
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.headerTitle}>Anketler</Text>

      <ScrollView contentContainerStyle={styles.listContainer}>
        {surveys.map((survey) => (
          <TouchableOpacity
            key={survey.id}
            style={styles.card}
            onPress={() => openSurvey(survey)}
          >
            <View style={styles.cardHeader}>
              <Text style={styles.surveyTitle}>{survey.title}</Text>
              {survey.answered && (
                <View style={[styles.badge, { backgroundColor: Colors.success }]}>
                  <Text style={styles.badgeText}>Cevaplandı</Text>
                </View>
              )}
            </View>
            <View style={styles.infoRow}>
              <FileText size={16} color={Colors.textLight} />
              <Text style={styles.infoText}>{survey.questionCount} Soru</Text>
            </View>
            <View style={styles.infoRow}>
              <Calendar size={16} color={Colors.textLight} />
              <Text style={styles.infoText}>Son Tarih: {survey.endDate}</Text>
            </View>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* Anket Formu Modal */}
      <Modal visible={modalVisible} animationType="slide" presentationStyle="pageSheet">
        <SafeAreaView style={styles.modalContainer}>
          <View style={styles.modalHeader}>
            <Text style={styles.modalTitle}>{selectedSurvey?.title}</Text>
            <TouchableOpacity onPress={() => setModalVisible(false)}>
              <X size={24} color={Colors.text} />
            </TouchableOpacity>
          </View>
          
          <ScrollView contentContainerStyle={styles.modalContent}>
            {selectedSurvey?.questions?.map(renderQuestion)}
          </ScrollView>

          <View style={styles.modalFooter}>
            <TouchableOpacity style={styles.submitButton} onPress={submitSurvey}>
              <Text style={styles.submitButtonText}>Gönder</Text>
            </TouchableOpacity>
          </View>
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Colors.text,
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 10,
  },
  listContainer: {
    padding: 20,
  },
  card: {
    backgroundColor: Colors.surface,
    borderRadius: 16,
    padding: 16,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: Colors.border,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  surveyTitle: {
    flex: 1,
    fontSize: 16,
    fontWeight: 'bold',
    color: Colors.text,
    marginRight: 10,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  badgeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  infoText: {
    marginLeft: 8,
    fontSize: 14,
    color: Colors.textLight,
  },
  modalContainer: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.text,
  },
  modalContent: {
    padding: 20,
  },
  questionContainer: {
    marginBottom: 24,
  },
  questionText: {
    fontSize: 16,
    fontWeight: '600',
    color: Colors.text,
    marginBottom: 12,
  },
  ratingContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  optionButton: {
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: Colors.border,
    marginBottom: 8,
  },
  optionSelected: {
    backgroundColor: Colors.primary,
    borderColor: Colors.primary,
  },
  optionText: {
    fontSize: 14,
    color: Colors.text,
  },
  optionTextSelected: {
    color: '#fff',
    fontWeight: 'bold',
  },
  textInput: {
    borderWidth: 1,
    borderColor: Colors.border,
    borderRadius: 8,
    padding: 12,
    color: Colors.text,
    minHeight: 100,
    textAlignVertical: 'top',
  },
  modalFooter: {
    padding: 20,
    borderTopWidth: 1,
    borderTopColor: Colors.border,
  },
  submitButton: {
    backgroundColor: Colors.primary,
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
  },
  submitButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: 'bold',
  },
});
