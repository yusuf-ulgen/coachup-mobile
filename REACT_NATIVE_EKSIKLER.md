# CoachUP — React Native Eksikler Raporu
> Kotlin Android → React Native 1:1 geçiş için tüm eksiklikler
>
> **Tarih:** 07.08.2026  
> **İncelenen Ekran Sayısı:** 28  
> **Toplam Eksiklik:** 85+

---

## Renk Kodu
- 🔴 **Eksik Özellik** — Tamamen yok -- ÇÖZÜLDÜ --
- 🟠 **Fonksiyonel Eksiklik** — UI var, logic/behavior eksik -- ÇÖZÜLDÜ --
- 🟡 **Tasarım Farkı** — Var ama görsel olarak farklı -- ÇÖZÜLDÜ --
- ⚪ **Placeholder** — Hardcoded/sahte veri konulmuş -- ÇÖZÜLDÜ --

---

## 🚨 KRİTİK GÜVENLİK SORUNU
 -- ÇÖZÜLDÜ --
> **QR Giriş Güvenlik Açığı** — `src/services/qrService.ts`
>
> `recordEntry` fonksiyonu Supabase Edge Function (`qr-validate`) çağırmak yerine
> doğrudan `gym_entries` tablosuna SQL INSERT atıyor.
> TOTP zaman penceresi, üyelik aktiflik ve açık giriş kaydı kontrolü **bypass** ediliyor.
>
> **Düzeltme:** `qr-validate` ve `qr-exit` Edge Function larını çağır.

---

## 1. AUTH / SPLASH

### Login Screen — src/screens/auth/LoginScreen.tsx
- 🔴 Klavye odak gecisi yok — Email->Sifre ImeAction.Next, Done->login -- ÇÖZÜLDÜ --
- 🔴 Basarili giriste ensureProfileFromAuthIfMissing() cagirisi yok -- ÇÖZÜLDÜ --
- 🔴 E-posta dogrulanmamis durumu icin ozel dialog yok — sadece Alert.alert var -- ÇÖZÜLDÜ --
- 🔴 Internet hatasi ozel yakalama yok — "Internet baglantisi yok" mesaji eksik -- ÇÖZÜLDÜ --
- 🟠 Loading esnasinda sag ok dairesi kayboluyor -- ÇÖZÜLDÜ --
- 🟠 Ekrana dokunarak klavye kapanmiyor -- ÇÖZÜLDÜ --
- 🟠 Android IME padding yok -- ÇÖZÜLDÜ --
- 🟡 Hero gorsel orani: Kotlin %50 vs RN %45 -- ÇÖZÜLDÜ --
- 🟡 Sifre goz ikonu absolute konumda — uzun sifrede metin ikonun altina giriyor -- ÇÖZÜLDÜ --

### Register Screen — src/screens/auth/RegisterScreen.tsx
- 🔴 Kayit sonrasi UserService.ensureProfileExists() yok -- ÇÖZÜLDÜ --
- 🔴 Dialog kapaninca AuthService.signOut() cagirilmiyor — dogrulanmamis oturum kaliyor -- ÇÖZÜLDÜ --
- 🔴 Klavye odak zinciri yok — Isim->Email->Sifre ImeAction.Next -- ÇÖZÜLDÜ --
- 🟠 Loading esnasinda ok dairesi kayboluyor -- ÇÖZÜLDÜ --
- 🟠 "Tekrar Gonder" loading state gosterilmiyor -- ÇÖZÜLDÜ --
- 🟡 Hero gorsel orani: %40 vs %38 -- ÇÖZÜLDÜ --
- 🟡 Cinsiyet buton yuksekligi: 56dp vs 54px -- ÇÖZÜLDÜ --

### Splash Screen — src/components/SplashView.tsx
- 🔴 Native Android 12+ installSplashScreen() entegrasyonu yok -- ÇÖZÜLDÜ --
- 🟡 Watermark spacing farki: 2dp vs 4px -- ÇÖZÜLDÜ --

---

## 2. ROOT NAVIGATOR — src/navigation/AppNavigator.tsx
- 🔴 Veli (Guardian) rolu tespiti yok — GuardianService.isGuardian() + GuardianScreen yonlendirmesi eksik -- ÇÖZÜLDÜ --
- 🔴 Pusher Realtime abonelikleri yok — subscribeToUser / subscribeToGym -- ÇÖZÜLDÜ --
- 🔴 Floating Active Workout Overlay yok — aktif antrenmanda sag altta suruklenmebilir kart -- ÇÖZÜLDÜ --
- 🔴 Bildirim ve Streak servis baslaticilar yok — maybySendEveningReminder, NotificationManager -- ÇÖZÜLDÜ --

---

## 3. HOME SCREEN — src/screens/home/HomeScreen.tsx
- 🔴 Grup Dersi Karti render edilmiyor — groupBookings veri cekilse de UI da hic gosterilmiyor -- ÇÖZÜLDÜ --
- 🔴 Bildirim rozeti yok — HomeScreen.tsx kendi inline header kullaniyor, Header.tsx kullanilmiyor -- ÇÖZÜLDÜ --
- 🟠 Program filtrelemesi yok — CalendarContentFilter.joinedPrograms() uygulanmiyor -- ÇÖZÜLDÜ --
- 🟠 Otomatik Streak Sync yok — StreakService.syncUserStreak() tetiklenmiyor -- ÇÖZÜLDÜ --
- 🟡 Drawer kapanma: repeatOnLifecycle(RESUMED) dinleyicisi yok -- ÇÖZÜLDÜ --

### Side Menu — src/components/SideMenu.tsx
- 🟠 Bireysel kullanici menu filtresi yok — is_individual olanlara Grup Dersleri/Rezervasyon/Odemeler gizlenmeli; 13 menu herkese gorunuyor -- ÇÖZÜLDÜ --
- 🟡 Logout dialogu: Compose AlertDialog vs Native Alert.alert -- ÇÖZÜLDÜ --

---

## 4. TAKVİM — src/screens/calendar/CalendarScreen.tsx
- 🔴 Acik grup derslerini listeleme yok — GroupClassService.fetchClassesForDate eksik -- ÇÖZÜLDÜ --
- 🔴 Derse Katil / Bekleme Listesi yok — sadece "Ayril" var -- ÇÖZÜLDÜ --
- 🔴 Bireysel vs Salon filtresi yok — CalendarContentFilter + effectiveShowAllEvents eksik -- ÇÖZÜLDÜ --
- 🔴 Etkinlik renk ve ikon secimi yok — 6 renk + 11 ikon; su an sabit mavi + takvim ikonu -- ÇÖZÜLDÜ --
- 🔴 Etkinlik not alani yok -- ÇÖZÜLDÜ --
- 🔴 Snackbar / Toast bildirimi yok — sadece console.error var -- ÇÖZÜLDÜ --
- 🔴 Takvim noktalari eksik — Grup Dersi ve Salon Etkinligi gunleri nokta almıyor -- ÇÖZÜLDÜ --
- 🟠 Kontenjan / doluluk gostergesi yok — "Dolu", "Yedekte" badge leri eksik -- ÇÖZÜLDÜ --
- 🟠 Gecmis ders "Bitti" kontrolu yok — gecmis derslerde "Ayril" aktif kaliyor -- ÇÖZÜLDÜ --
- 🟠 Program turu renklendirme sabit — Cardio/Strength/Flexibility ayirimi yok; hep mor -- ÇÖZÜLDÜ --
- 🟠 Saat secimi: Dropdown slot picker yerine duz TextInput kullaniliyor -- ÇÖZÜLDÜ --
- 🟠 Modal loading state yok — kaydet butonunda CircularProgressIndicator yok -- ÇÖZÜLDÜ --
- 🟡 Takvim grid: 42 hucre (onceki/sonraki ay) vs sadece mevcut ayin gunleri -- ÇÖZÜLDÜ --

---

## 5. ANTRENMAN — EN BUYUK GAP

### Training Screen — src/screens/training/TrainingScreen.tsx
- 🟠 Aktif antrenman kontrolu yok — Aktivitelere tiklaninca kontrol yapilmiyor -- ÇÖZÜLDÜ --
- 🟠 Program siralama ve filtreleme logic eksik -- ÇÖZÜLDÜ --
- 🟡 Program kart butonu: tam genislik "Programi Baslat" vs kucuk "Baslat" -- ÇÖZÜLDÜ --

### Active Workout Screen — src/screens/training/ActiveWorkoutScreen.tsx
- 🔴 GPS + Google Maps entegrasyonu yok — rota, mesafe, tempo, hiz, yukseklik, auto-pause, splits -- ÇÖZÜLDÜ --
- 🔴 Supabase set ve egzersiz takibi yok — HARDCODED 4 SAHTE SET kullaniliyor; DB ye hic kayit atilmiyor -- ÇÖZÜLDÜ --
- 🔴 Health Connect / Nabiz entegrasyonu yok — kalori Math.ceil((seconds/60)*8) formulu kullaniliyor -- ÇÖZÜLDÜ --
- 🔴 Set arasi dinlenme sayaci yok — CircularRestTimer hic yazilmamis -- ÇÖZÜLDÜ --
- 🔴 Program onizleme overlay yok -- ÇÖZÜLDÜ --
- 🔴 Efor derecelendirme (RPE) modali yok -- ÇÖZÜLDÜ --
- 🔴 ActiveWorkoutManager yok — sayfadan cikinca sayac duruyor -- ÇÖZÜLDÜ --

### RecordAttempt Setup — src/screens/training/RecordAttemptSetupScreen.tsx
- 🔴 Supabase dan kategori ve egzersiz cekilmiyor — hardcoded diziler kullaniliyor -- ÇÖZÜLDÜ --
- 🔴 Kullanicinin gecmis PR verisi gosterilmiyor -- ÇÖZÜLDÜ --
- 🔴 Plaka hesaplayici ve isinma seti plani yok -- ÇÖZÜLDÜ --
- 🔴 Sadece 2 rekor tipi (Agirlik, Tekrar) — Sure/Mesafe/Kalori eksik -- ÇÖZÜLDÜ --
- 🔴 Uzun basma destegi yok -- ÇÖZÜLDÜ --

### Record Attempt Session Screen — TAMAMEN YOK
- 🔴 RecordAttemptSessionScreen hic yazilmamis — RPESlider, PlateVisual, Yaptim/Yapamadim aksiyonlari -- ÇÖZÜLDÜ --

### Record Attempt Timed Modes — TAMAMEN YOK
- 🔴 RecordAttemptTimedModes hic yazilmamis — 3-2-1 geri sayim, Cindy AMRAP, GPS kosу rekoru -- ÇÖZÜLDÜ --

### Record Attempt Summary — TAMAMEN YOK
- 🔴 RecordAttemptSummaryScreen hic yazilmamis — 1RM hesabi, RPE sparkline, PR konfeti -- ÇÖZÜLDÜ --

### Workout Summary Screen — TAMAMEN YOK
- 🔴 Hic yazilmamis: Sure/Kalori/Nabiz kartlari, HR Zones grafigi, rota ozeti, splits tablosu, progressive overload onerisi -- ÇÖZÜLDÜ --

### Workout Finish Celebration — TAMAMEN YOK
- 🔴 Strava tarzi tam ekran animasyonlu kutlama hic yazilmamis -- ÇÖZÜLDÜ --

### Workout Share Sheet — TAMAMEN YOK
- 🔴 4 sarlon sosyal medya paylasiom kartı (Rota, Detayli istatistik, Minimal, Story), Canvas render hic yazilmamis -- ÇÖZÜLDÜ --

### Eksik Bilesenler (Hic Yazilmamis)
- 🔴 AttemptSetRow — isinma/ana set satiri -- ÇÖZÜLDÜ --
- 🔴 CircularRestTimer — set arasi dairsel sayac -- ÇÖZÜLDÜ --
- 🔴 ConfettiView — PR kirilan konfeti animasyonu -- ÇÖZÜLDÜ --
- 🔴 PlateVisualView — halter plaka gorseli -- ÇÖZÜLDÜ --
- 🔴 RPESlider — zorluk skoru slider -- ÇÖZÜLDÜ --

---

## 6. TOPLULUK — src/screens/community/CommunityScreen.tsx
- 🔴 Aktif salon ID cozumleme yok — userProfile.gym_id direkt okunuyor, resolveActiveGymIdForContent eksik -- ÇÖZÜLDÜ --
- 🔴 Yorum hata bildirimi yok — catch blogu TAMAMEN BOS (silent fail) -- ÇÖZÜLDÜ --
- 🟠 Anket %0 oylanan secenekte bar tamamen kayboluyor -- ÇÖZÜLDÜ --
- 🟡 Gonderi silme: Kotlin direkt siliyor vs RN onay kutusu aciyor (RN daha iyi) -- ÇÖZÜLDÜ --
- 🟡 Composer butonlar: sadece ikon vs ikon + metin (RN daha iyi) -- ÇÖZÜLDÜ --

---

## 7. QR TARAMA — src/screens/qr/QREntryScreen.tsx
- 🟠 GUVENLIK: Edge Function bypass — direkt SQL INSERT; bkz. kritik bolum -- ÇÖZÜLDÜ --
- 🟠 Hedef tablo uyusmazligi — qr_entries vs gym_entries -- ÇÖZÜLDÜ --
- 🟠 "Tumunu Gor" sayisi yaniltici — max 5 kayit cekiliyor, gercek sayi gosterilemiyor -- ÇÖZÜLDÜ --
- 🟡 Filtre cipleri: LazyRow pill vs 3 esit sutun grid -- ÇÖZÜLDÜ --
- 🟡 Hata popup: AlertDialog vs ozel Modal -- ÇÖZÜLDÜ --

---

## 8. PROFİL — src/screens/profile/ProfileScreen.tsx
- 🔴 Profil duzenleme form alanlari yok — Ad, Soyad, Email, Telefon, Dogum, Cinsiyet, Boy, Kilo -- ÇÖZÜLDÜ --
- 🔴 Profil fotografı yukleme yok — Kamera/Galeri + Supabase Storage avatar upload -- ÇÖZÜLDÜ --
- 🟠 Salt okunur ekran; hicbir etkilesim yok -- ÇÖZÜLDÜ --

---

## 9. AYARLAR — TAMAMEN YOK (dosya bile yok)

### Settings Screen
- 🔴 Varsayilan giris ekrani secimi -- ÇÖZÜLDÜ --
- 🔴 Bildirimler Toggle -- ÇÖZÜLDÜ --
- 🔴 Biyometrik Dogrulama Toggle -- ÇÖZÜLDÜ --
- 🔴 Agirlik Birimi Toggle (kg/lbs) -- ÇÖZÜLDÜ --

### Address Screen
- 🔴 81 il dropdown, Ilce, Mahalle, Cadde, Bina No, Daire No, Posta Kodu -- ÇÖZÜLDÜ --

### Appearance Settings
- 🔴 Acik / Karanlik / Sistem temasi secimi -- ÇÖZÜLDÜ --

### Password Settings
- 🔴 Mevcut Sifre, Yeni Sifre, Sifre Tekrar + dogrulama logic -- ÇÖZÜLDÜ --

---

## 10. UYELİK — MenuSubScreens.tsx (MembershipScreen)
- ⚪ TAMAMEN SAHTE VERİ — "CoachUP Performans Kulubu", "Yillik Premium Sporcu", "31.12.2026" -- ÇÖZÜLDÜ --
- 🔴 Dinamik uyelik paketi DB den cekme yok -- ÇÖZÜLDÜ --
- 🔴 Plan Sec/Yenile dialogu ve membership_requests kaydı yok -- ÇÖZÜLDÜ --

---

## 11. BİLDİRİMLER — src/screens/notifications/NotificationsScreen.tsx
- 🟡 Bildirim turune gore ozel ikon yok — herkese sabit Bell ikonu -- ÇÖZÜLDÜ --
- 🟠 Tekil bildirim okundu/silme aksiyonu yok -- ÇÖZÜLDÜ --

---

## 12. KOCLAR — src/screens/coaches/CoachListScreen.tsx
- 🔴 Arama cubugu yok -- ÇÖZÜLDÜ --
- 🔴 Cinsiyet filtresi yok (Tumu/Erkek/Kadin) -- ÇÖZÜLDÜ --
- 🟠 "Randevu" butonu tiklanamıyor -- ÇÖZÜLDÜ --

### Coach Detail Screen — TAMAMEN YOK
- 🔴 Biyografi, Rating, Uzmanlik Alanlari, Sertifikalar, Mesaj Gonder butonu -- ÇÖZÜLDÜ --

### Coach Chat Screen — TAMAMEN YOK
- 🔴 Supabase Realtime anlik mesajlasma -- ÇÖZÜLDÜ --
- 🔴 Kullanici/koc mesaj balonlari -- ÇÖZÜLDÜ --
- 🔴 Optimistik UI guncelleme -- ÇÖZÜLDÜ --
- 🔴 Okundu/iletildi ikonlari -- ÇÖZÜLDÜ --
- 🔴 Mesaj giris cubugu -- ÇÖZÜLDÜ --

---

## 13. RANDEVULARIM — TAMAMEN YOK
- 🔴 Yaklasan ve Gecmis randevular tablari -- ÇÖZÜLDÜ --
- 🔴 Yeni Randevu Dialogu — Koc secimi, Randevu Turu, Tarih, Saat, Not -- ÇÖZÜLDÜ --
- 🔴 Randevu Iptal Dialogu -- ÇÖZÜLDÜ --

---

## 14. ODEMELER — TAMAMEN YOK
- 🔴 Aktif Taksit Plani karti — toplam tutar, taksit adedi, vade tarihi -- ÇÖZÜLDÜ --
- 🔴 Taksit listesi — Odendi ve Geckmis gostergesi -- ÇÖZÜLDÜ --
- 🔴 Finansal islem gecmisi -- ÇÖZÜLDÜ --

---

## 15. HEDEFLERİM — MenuSubScreens.tsx (GoalsScreen)
- 🔴 Aktif/Tamamlanan tablari yok -- ÇÖZÜLDÜ --
- 🔴 Yeni Hedef Ekleme dialogu yok -- ÇÖZÜLDÜ --
- 🔴 Hedef ilerleme bari yok -- ÇÖZÜLDÜ --
- 🔴 "Tamamlandi olarak isaretler" butonu yok -- ÇÖZÜLDÜ --
- 🟠 Salt okunur ScrollView — etkilesim yok -- ÇÖZÜLDÜ --

---

## 16. REZERVASYONLARIM — TAMAMEN YOK
- 🔴 Aktif rezervasyonlar — Onaylandi/Bekliyor/Iptal, Iptal Et butonu -- ÇÖZÜLDÜ --
- 🔴 Rezerve edilebilir salon alanlari — Studyo, Havuz, Sauna, Kort + kapasiteler -- ÇÖZÜLDÜ --
- 🔴 Rezervasyon Yapma Dialogu -- ÇÖZÜLDÜ --

---

## 17. SONUCLAR — TAMAMEN YOK

### Results Screen
- 🔴 Toplam Egzersiz, Max Agirlik, Max 1RM ozet bandi -- ÇÖZÜLDÜ --
- 🔴 KG/LBS toggle -- ÇÖZÜLDÜ --
- 🔴 Sonuclarim vs Tum Egzersizler tablari -- ÇÖZÜLDÜ --
- 🔴 Egzersiz arama + sonuc kartlari -- ÇÖZÜLDÜ --

### Result Detail Screen — TAMAMEN YOK
- 🔴 Max Agirlik/Tekrar/1RM karti -- ÇÖZÜLDÜ --
- 🔴 1RM Yuzdelikler Grid (%100, %90, %80...) -- ÇÖZÜLDÜ --
- 🔴 90 gunluk Canvas cizgi grafigi -- ÇÖZÜLDÜ --
- 🔴 Yillara gore grusplanmis set/tekrar gecmisi -- ÇÖZÜLDÜ --

---

## 18. KİŞİSEL REKORLAR — MenuSubScreens.tsx (PersonalRecordsScreen)
- ⚪ TAMAMEN SAHTE VERİ — Bench 110kg, Squat 150kg, Deadlift 180kg hardcoded -- ÇÖZÜLDÜ --
- 🔴 Antrenmanlar vs Kisisel Rekorlar tablari yok -- ÇÖZÜLDÜ --
- 🔴 Streak sayaci yok -- ÇÖZÜLDÜ --
- 🔴 Interaktif aylik aktivite takvimi yok -- ÇÖZÜLDÜ --
- 🔴 Zaman filtreleri yok -- ÇÖZÜLDÜ --
- 🔴 "Rekor Denemesi Baslat" butonu yok -- ÇÖZÜLDÜ --

---

## 19. SERİ (Streak) — TAMAMEN YOK
- 🔴 Buyuk alev ikonu ve gun sayisi -- ÇÖZÜLDÜ --
- 🔴 En Uzun Seri -- ÇÖZÜLDÜ --
- 🔴 Milestone Rozetleri — 3, 7, 14, 30, 50, 100 gun -- ÇÖZÜLDÜ --
- 🔴 Son 7 Gun Aktivite Izgara -- ÇÖZÜLDÜ --
- 🔴 Son tamamlanan aktiviteler listesi -- ÇÖZÜLDÜ --
- 🔴 Motivasyon mesaji karti -- ÇÖZÜLDÜ --

---

## 20. BESLENME — TAMAMEN YOK
- 🔴 Canvas Kalori Halkasi — Tuketilen vs Hedef animasyonlu halka -- ÇÖZÜLDÜ --
- 🔴 Makro Dagilim karti — Protein/Karbonhidrat/Yag gramajlari -- ÇÖZÜLDÜ --
- 🔴 Aktif diyet plani ozeti -- ÇÖZÜLDÜ --
- 🔴 Ogun Kartlari — Kahvalti, Ogle, Aksam, Ara ogun; genisletilebilir detaylar -- ÇÖZÜLDÜ --
- 🔴 FatSecret API entegrasyonlu Besin Arama ve Gunluge Ekleme -- ÇÖZÜLDÜ --

---

## 21. FİZİKSEL OLCUMLER — TAMAMEN YOK
- 🔴 Olcumler/Fotograflar Segmented Control -- ÇÖZÜLDÜ --
- 🔴 Son Olcum Karti — Kilo, Yag%, BMI -- ÇÖZÜLDÜ --
- 🔴 Kilo ilerleme bar grafigi -- ÇÖZÜLDÜ --
- 🔴 Vucut olcum gecmisi — Gogus, Bel, Kalca, Kol, Bacak (cm) -- ÇÖZÜLDÜ --
- 🔴 Yeni Olcum Ekleme Dialogu -- ÇÖZÜLDÜ --
- 🔴 Oncesi/Sonrasi Fotograf Slot Kartlari — Kamera/Galeri + Supabase Storage -- ÇÖZÜLDÜ --

---

## 22. GRUP DERSLERİ — TAMAMEN YOK
- 🔴 Haftalik Gun Secici (Pzt-Paz) -- ÇÖZÜLDÜ --
- 🔴 Grup Dersi Kartlari — Ders adi, Egitmen, Saat, Konum, Katilimci Kapasitesi -- ÇÖZÜLDÜ --
- 🔴 Katilim Durumu Rozetleri — "Katildin", "Yedekte" -- ÇÖZÜLDÜ --
- 🔴 Katil / Ayril / Bekleme Listesi (Waitlist) -- ÇÖZÜLDÜ --

---

## 23. ANKETLER — TAMAMEN YOK
- 🔴 Aktif anketler listesi — Soru sayisi, Bitis tarihi, "Cevaplandi" rozeti -- ÇÖZÜLDÜ --
- 🔴 Anket Formu Dialogu — Puanlama (1-5/1-10), Coktan secmeli, Metin sorular -- ÇÖZÜLDÜ --
- 🔴 Cevaplari Supabase e kaydetme logic -- ÇÖZÜLDÜ --

---

## 24. VELİ MODU (Guardian) — TAMAMEN YOK (6 Ekran)
- 🔴 GuardianScreen — veli giris noktasi, Bottom TabBar -- ÇÖZÜLDÜ --
- 🔴 GuardianHomeScreen — cocuklarin gunluk aktivite ozeti -- ÇÖZÜLDÜ --
- 🔴 GuardianChildrenScreen — cocuklarin giris/cikis hareket takibi -- ÇÖZÜLDÜ --
- 🔴 GuardianChildDetailScreen — cocuk detay, aktif uyelikler, ders katilimlar -- ÇÖZÜLDÜ --
- 🔴 GuardianPaymentsScreen — veli odemeleri akisi -- ÇÖZÜLDÜ --
- 🔴 GuardianProfileScreen — veli profili -- ÇÖZÜLDÜ --

---

## 25. ADMİN PANELİ — TAMAMEN YOK
- 🔴 AdminDashboardScreen — navigasyonda GenericMenuScreen placeholder var, gercek ekran yok -- ÇÖZÜLDÜ --

---

## OZET TABLO

| Ekran | Durum |
|-------|-------|
| Login | -- ÇÖZÜLDÜ -- |
| Register | -- ÇÖZÜLDÜ -- |
| Splash | -- ÇÖZÜLDÜ -- |
| Root Navigator | -- ÇÖZÜLDÜ -- |
| Home | -- ÇÖZÜLDÜ -- |
| SideMenu | -- ÇÖZÜLDÜ -- |
| Takvim | -- ÇÖZÜLDÜ -- |
| TrainingScreen | -- ÇÖZÜLDÜ -- |
| ActiveWorkout | -- ÇÖZÜLDÜ -- |
| RecordAttempt Session | -- ÇÖZÜLDÜ -- |
| RecordAttempt Timed | -- ÇÖZÜLDÜ -- |
| RecordAttempt Summary | -- ÇÖZÜLDÜ -- |
| WorkoutSummary | -- ÇÖZÜLDÜ -- |
| WorkoutFinishCelebration | -- ÇÖZÜLDÜ -- |
| WorkoutShareSheet | -- ÇÖZÜLDÜ -- |
| Topluluk | -- ÇÖZÜLDÜ -- |
| QR Tarama | -- ÇÖZÜLDÜ -- |
| Profil | -- ÇÖZÜLDÜ -- |
| Ayarlar (tumu) | -- ÇÖZÜLDÜ -- |
| Uyelik | -- ÇÖZÜLDÜ -- |
| Bildirimler | -- ÇÖZÜLDÜ -- |
| Koclar Listesi | -- ÇÖZÜLDÜ -- |
| Koc Detay | -- ÇÖZÜLDÜ -- |
| Koc Chat | -- ÇÖZÜLDÜ -- |
| Randevularim | -- ÇÖZÜLDÜ -- |
| Odemeler | -- ÇÖZÜLDÜ -- |
| Hedeflerim | -- ÇÖZÜLDÜ -- |
| Rezervasyonlarim | -- ÇÖZÜLDÜ -- |
| Sonuclar | -- ÇÖZÜLDÜ -- |
| Sonuc Detay | -- ÇÖZÜLDÜ -- |
| Kisisel Rekorlar | -- ÇÖZÜLDÜ -- |
| Seri (Streak) | -- ÇÖZÜLDÜ -- |
| Beslenme | -- ÇÖZÜLDÜ -- |
| Fiziksel Olcumler | -- ÇÖZÜLDÜ -- |
| Grup Dersleri | -- ÇÖZÜLDÜ -- |
| Anketler | -- ÇÖZÜLDÜ -- |
| Veli Modu (6 ekran) | -- ÇÖZÜLDÜ -- |
| Admin Paneli | -- ÇÖZÜLDÜ -- |

---

*Rapor 7 paralel AI agent tarafından tüm Kotlin ve React Native kaynak dosyalari okunarak hazirlanmistir.*






