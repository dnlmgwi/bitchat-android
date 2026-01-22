**ADDITIONAL NON-IDENTIFYING DATA**

Expanding Analytics to Solve Malawi Music Industry Problems

*Technical Appendix to Offline Music Analytics Specification*

# **1\. Overview**

Your current specification captures playback events and transfer metadata. However, there are numerous additional data points that can be collected **without identifying individuals** that would provide immense value to artists, COSOMA, distributors, and the broader ecosystem.

This document identifies 8 categories of additional non-PII data, maps each to specific industry problems, and provides implementation guidance.

# **2\. Listening Context Data**

**Problem Solved:** Artists and labels don't know WHEN or HOW their music is consumed, making marketing and release timing guesswork.

| Data Field | What It Captures | Business Value |
| :---- | :---- | :---- |
| time\_of\_day\_bucket | Morning/Afternoon/Evening/Night playback | Optimize release times; radio scheduling insights |
| day\_of\_week | Weekday vs weekend patterns | Plan promotional activities; understand work vs leisure listening |
| session\_duration | How long users listen in one sitting | Gauge engagement depth; identify binge-listening content |
| playback\_mode | Shuffle vs sequential vs repeat | Understand album vs playlist consumption; curate better |
| volume\_level\_avg | Average volume during playback | Infer context (quiet office vs loud transport) |
| audio\_output\_type | Speaker vs headphones (if detectable) | Personal vs social listening; event planning insights |

### **Implementation Notes**

* Time bucketing (4-hour windows) prevents precise activity tracking while preserving patterns  
* Volume levels averaged per session, not per-second tracking  
* Audio output detected via Android AudioManager.isWiredHeadsetOn() or Bluetooth A2DP connection

# **3\. Library Composition Data**

**Problem Solved:** No understanding of what music collections look like across Malawi—how diverse, how large, how current.

| Data Field | What It Captures | Business Value |
| :---- | :---- | :---- |
| library\_size | Total tracks on device | Market segmentation; storage planning for apps |
| unique\_artists\_count | Artist diversity in library | Identify mono-fans vs eclectics; fandom depth |
| genre\_distribution | % breakdown by genre (auto-classified) | Genre market sizing; trend identification |
| local\_vs\_international | Malawian vs foreign content ratio | Local content consumption metrics for COSOMA/policy |
| catalog\_age\_profile | Distribution of track release years | Nostalgia vs new release consumption patterns |
| album\_completeness | Full albums vs singles ratio | Album vs track purchasing behavior |
| avg\_file\_quality | Average bitrate/format distribution | Quality expectations; infrastructure for streaming |

### **Implementation Notes**

* Genre classification via lightweight ML model or audio feature analysis (tempo, instrumentation)  
* Local vs international detection via artist name matching against known Malawian artist database  
* Computed once per week and synced as aggregate stats, not per-track data

# **4\. Music Spread & Virality Data**

**Problem Solved:** Piracy spreads music but provides zero data. Artists don’t know HOW their music spreads or which distribution points matter.

| Data Field | What It Captures | Business Value |
| :---- | :---- | :---- |
| first\_seen\_to\_spread\_days | Days from track first appearing to reaching N devices | Measure virality speed; identify breakout tracks early |
| spread\_velocity | Rate of new device appearances per day | Trend detection; viral moment identification |
| origin\_hub\_id | Which aggregator first saw the track | Identify tastemaker burning centers |
| geographic\_spread\_pattern | Order of regions track appeared in | Map distribution networks; target marketing |
| co\_transfer\_tracks | Which tracks are frequently transferred together | Understand bundling; playlist/compilation insights |
| transfer\_batch\_size\_avg | Average tracks transferred in one session | Burning center behavior; bulk vs selective acquisition |

### **Why This Matters**

This is **the most unique data your system can provide**. Streaming platforms can’t track offline distribution networks. For the first time, artists could see: “Your song started in Lilongwe, reached Blantyre in 3 days, and spread to rural Southern Region within 2 weeks.” This proves organic popularity independent of radio play.

# **5\. Engagement Quality Metrics**

**Problem Solved:** Play counts don’t distinguish between loved tracks and background noise. Artists need to know what resonates.

| Data Field | What It Captures | Business Value |
| :---- | :---- | :---- |
| skip\_velocity | How quickly into track users skip (seconds) | Identify weak intros; optimize song structure |
| completion\_rate | % of plays that reach end | True engagement metric; separates hits from filler |
| replay\_within\_session | Same track played again within hour | Identify addictive tracks; emotional resonance |
| seek\_events | Forward/backward seeking within track | Identify memorable sections; remix opportunities |
| track\_retention\_days | Days track stays on device before deletion | Long-term value vs novelty |
| add\_to\_favorites | User favoriting/starring actions | Explicit preference signal |

### **Royalty Weighting Application**

COSOMA could implement **quality-weighted royalties**: tracks with high completion rates and replays earn more per play than tracks frequently skipped. This incentivizes quality over clickbait and aligns artist incentives with listener satisfaction.

# **6\. Audio Content Analysis (Non-Listening)**

**Problem Solved:** No systematic understanding of Malawian music characteristics—production quality, genre evolution, or market gaps.

| Data Field | What It Captures | Business Value |
| :---- | :---- | :---- |
| tempo\_bpm | Beats per minute | Genre classification; DJ/playlist matching |
| key\_signature | Musical key detected | DJ mixing compatibility; mood mapping |
| loudness\_lufs | Integrated loudness (mastering quality) | Production quality scoring; identify poorly mastered tracks |
| dynamic\_range | Difference between loud and quiet sections | Production sophistication indicator |
| language\_detected | Chichewa/English/Tumbuka/other | Language market segmentation |
| vocal\_instrumental\_ratio | Percentage of track with vocals | Instrumental vs vocal market analysis |
| energy\_profile | High/medium/low energy classification | Mood-based recommendations; context matching |

### **Production Quality Scoring**

Combining loudness, dynamic range, and frequency analysis, the system could generate a **Production Quality Score** for each track. This helps: (1) Artists identify if their production is competitive, (2) COSOMA flag potentially pirated/degraded copies, (3) Burning centers promote quality content.

# **7\. Network Topology Data**

**Problem Solved:** The informal distribution network is invisible. Who are the influential nodes? How does music flow?

| Data Field | What It Captures | Business Value |
| :---- | :---- | :---- |
| device\_transfer\_out\_count | How many transfers originated from device | Identify “super spreaders” (anonymized) |
| device\_transfer\_in\_count | How many transfers received | Identify consumers vs distributors |
| hub\_reach\_hops | How many Bluetooth hops to reach aggregator | Network density mapping; coverage gaps |
| cluster\_id | Anonymous group identifier for connected devices | Social cluster analysis without identification |
| sync\_frequency | How often device syncs with aggregators | Identify high-engagement users |

### **Influencer Identification (Anonymous)**

Devices with high transfer\_out\_count are music tastemakers. While you can’t identify WHO they are, you can: (1) See which burning centers they frequent, (2) Track which new tracks they spread first, (3) Use their behavior to predict breakout hits.

# **8\. Device & Market Context**

**Problem Solved:** No understanding of the device landscape—what hardware people use, storage constraints, capability tiers.

| Data Field | What It Captures | Business Value |
| :---- | :---- | :---- |
| device\_tier | Low/Medium/High spec classification | App optimization targets; feature availability |
| storage\_available | Free storage on device | Understand library size constraints |
| os\_version\_bucket | Android version range | API compatibility planning |
| screen\_size\_bucket | Small/Medium/Large | UI optimization; video content viability |
| battery\_during\_playback | Average battery level when playing | Optimize for low-battery scenarios |
| connectivity\_profile | Never/Rare/Sometimes/Often online | Feature availability; sync strategy |

### **Market Intelligence Value**

This data helps telecoms (Airtel, TNM) and device manufacturers understand the Malawian consumer. It’s valuable for: (1) Designing appropriate data bundles, (2) Targeting device promotions, (3) Planning infrastructure investments.

# **9\. Temporal & Trend Data**

**Problem Solved:** No historical data on music trends, seasonal patterns, or track lifecycles in the Malawian market.

| Data Field | What It Captures | Business Value |
| :---- | :---- | :---- |
| track\_lifecycle\_stage | Rising/Peak/Declining/Catalog classification | Release timing; catalog monetization |
| seasonal\_index | Playback patterns by month/season | Plan releases around festivals, holidays |
| days\_on\_chart | Days track has been in top N | Chart compilation; award eligibility data |
| new\_release\_adoption\_rate | Speed of new track uptake | Marketing effectiveness; artist fanbase strength |
| catalog\_longevity\_score | How long tracks stay in active rotation | Evergreen vs flash hits identification |

### **Creating the Malawian Charts**

With this data, you could publish **official Malawian music charts** based on actual consumption—not radio airplay or social media buzz. This becomes: (1) A media product itself (sell to newspapers, radio), (2) Credibility for artists booking shows, (3) Data for award nominations.

# **10\. Implementation Priority Matrix**

| Data Category | Dev Effort | Value | Priority |
| :---- | :---- | :---- | :---- |
| Listening Context | Low | Medium | Phase 1 \- Quick Win |
| Engagement Quality | Low | High | Phase 1 \- Critical for royalties |
| Music Spread/Virality | Medium | Very High | Phase 1 \- Unique differentiator |
| Library Composition | Medium | Medium | Phase 2 \- Market intelligence |
| Network Topology | Medium | High | Phase 2 \- Distribution insights |
| Audio Content Analysis | High | Medium | Phase 3 \- Requires ML |
| Device Context | Low | Low-Medium | Phase 3 \- Nice to have |
| Temporal Trends | Low | High | Phase 2 \- Enables charts |

# **11\. Privacy Safeguards**

All proposed data collection maintains the specification’s privacy-first approach:

1. **Bucketing:** Time, location, and numeric values are bucketed into ranges, not precise values  
2. **Aggregation:** Library stats computed locally, only summaries transmitted  
3. **Anonymization:** Device IDs remain hashed; network topology uses anonymous cluster IDs  
4. **Minimization:** No raw audio transmitted; only computed features  
5. **Purpose Limitation:** Data used only for royalty calculation and industry analytics

# **12\. Conclusion**

By expanding data collection to include these categories, your system transforms from a *royalty calculation tool* into a **comprehensive music industry intelligence platform**.

The most valuable additions are:

* **Music Spread/Virality Data** — No one else can provide this; it’s your unique asset  
* **Engagement Quality Metrics** — Enables quality-weighted royalties that benefit good artists  
* **Temporal Trend Data** — Powers official charts, a media product with its own revenue

Each additional data point increases the value proposition for COSOMA, Newwave, telecoms, and brands—all without compromising user privacy.

*— End of Document —*