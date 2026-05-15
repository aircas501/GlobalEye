package com.globaleyes.crawler.init;

import com.globaleyes.crawler.pojo.entity.RssSource;
import com.globaleyes.crawler.repository.crawl.RssSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RSS源数据初始化器
 * 在应用启动时初始化200+全球新闻RSS源
 *
 * @author RSS News Crawler Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class RssSourceInitializer implements CommandLineRunner {

    private final RssSourceRepository rssSourceRepository;

    /**
     * 构造函数注入Repository
     *
     * @param rssSourceRepository RSS源Repository
     */
    public RssSourceInitializer(RssSourceRepository rssSourceRepository) {
        this.rssSourceRepository = rssSourceRepository;
    }

    @Override
    public void run(String... args) {
        if (rssSourceRepository.count() > 0) {
            log.info("RSS sources already initialized, skipping...");
            return;
        }

        log.info("Initializing RSS sources...");
        List<RssSource> sources = createRssSources();

        int savedCount = 0;
        for (RssSource source : sources) {
            try {
                if (!rssSourceRepository.existsByUrl(source.getUrl())) {
                    rssSourceRepository.save(source);
                    savedCount++;
                }
            } catch (Exception e) {
                log.warn("Failed to save RSS source: {} - {}", source.getName(), e.getMessage());
            }
        }

        log.info("Initialized {} RSS sources", savedCount);
    }

    /**
     * 创建RSS源列表
     *
     * @return RSS源列表
     */
    private List<RssSource> createRssSources() {
        List<RssSource> sources = new ArrayList<>();

        sources.add(createSource("Reuters World News", "https://www.reutersagency.com/feed/?taxonomy=best-topics&post_type=best", "美国", "北美", "en", "news", "A"));
        sources.add(createSource("BBC World News", "https://feeds.bbci.co.uk/news/world/rss.xml", "英国", "欧洲", "en", "news", "A"));
        sources.add(createSource("CNN World", "https://rss.cnn.com/rss/edition_world.rss", "美国", "北美", "en", "news", "A"));
        sources.add(createSource("The Guardian World", "https://www.theguardian.com/world/rss", "英国", "欧洲", "en", "news", "A"));
        sources.add(createSource("New York Times World", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml", "美国", "北美", "en", "news", "A"));
        sources.add(createSource("Washington Post World", "https://feeds.washingtonpost.com/rss/world", "美国", "北美", "en", "news", "A"));
        sources.add(createSource("Wall Street Journal World", "https://feeds.a.dj.com/rss/RSSWorldNews.xml", "美国", "北美", "en", "news", "A"));
        sources.add(createSource("Al Jazeera English", "https://www.aljazeera.com/xml/rss/all.xml", "卡塔尔", "中东", "en", "news", "A"));
        sources.add(createSource("France 24", "https://www.france24.com/en/rss", "法国", "欧洲", "en", "news", "A"));
        sources.add(createSource("Deutsche Welle", "https://rss.dw.com/rdf/rss-en-all", "德国", "欧洲", "en", "news", "A"));

        sources.add(createSource("CCTV新闻", "https://news.cctv.com/world/rss.xml", "中国", "亚洲", "zh", "news", "A"));
        sources.add(createSource("人民网", "https://www.people.com.cn/rss/world.xml", "中国", "亚洲", "zh", "news", "A"));
        sources.add(createSource("新华网", "https://www.xinhuanet.com/world/news_world.xml", "中国", "亚洲", "zh", "news", "A"));
        sources.add(createSource("中国新闻网", "https://www.chinanews.com/rss/world.xml", "中国", "亚洲", "zh", "news", "A"));
        sources.add(createSource("环球网", "https://world.huanqiu.com/rss/world.xml", "中国", "亚洲", "zh", "news", "B"));

        sources.add(createSource("NHK World", "https://www3.nhk.or.jp/rss/news/cat0.xml", "日本", "亚洲", "ja", "news", "A"));
        sources.add(createSource("The Japan Times", "https://www.japantimes.co.jp/feed/", "日本", "亚洲", "en", "news", "B"));
        sources.add(createSource("Yonhap News", "https://en.yna.co.kr/RSS/news.xml", "韩国", "亚洲", "en", "news", "B"));
        sources.add(createSource("Korea Herald", "https://www.koreaherald.com/rss_xml.php", "韩国", "亚洲", "en", "news", "B"));

        sources.add(createSource("The Times of India", "https://timesofindia.indiatimes.com/rssfeeds/296589292.cms", "印度", "亚洲", "en", "news", "B"));
        sources.add(createSource("The Hindu", "https://www.thehindu.com/news/international/feeder/default.rss", "印度", "亚洲", "en", "news", "B"));
        sources.add(createSource("NDTV", "https://feeds.feedburner.com/ndtvnews-world-news", "印度", "亚洲", "en", "news", "B"));

        sources.add(createSource("ABC News", "https://abcnews.go.com/abcnews/internationalheadlines", "美国", "北美", "en", "news", "A"));
        sources.add(createSource("NBC News World", "https://feeds.nbcnews.com/nbcnews/public/world", "美国", "北美", "en", "news", "A"));
        sources.add(createSource("CBS News World", "https://www.cbsnews.com/latest/rss/world", "美国", "北美", "en", "news", "A"));
        sources.add(createSource("AP News", "https://r.jina.ai/http://feeds.apnews.com/rss/apf-topnews", "美国", "北美", "en", "news", "A"));

        sources.add(createSource("The Telegraph", "https://www.telegraph.co.uk/news/rss.xml", "英国", "欧洲", "en", "news", "A"));
        sources.add(createSource("The Independent", "https://www.independent.co.uk/news/world/rss", "英国", "欧洲", "en", "news", "B"));
        sources.add(createSource("Daily Mail World", "https://www.dailymail.co.uk/news/worldnews/index.rss", "英国", "欧洲", "en", "news", "B"));

        sources.add(createSource("Le Monde", "https://www.lemonde.fr/rss/une.xml", "法国", "欧洲", "fr", "news", "A"));
        sources.add(createSource("Le Figaro", "https://www.lefigaro.fr/rss/figaro_international.xml", "法国", "欧洲", "fr", "news", "A"));

        sources.add(createSource("Der Spiegel", "https://www.spiegel.de/international/index.rss", "德国", "欧洲", "de", "news", "A"));
        sources.add(createSource("Frankfurter Allgemeine", "https://www.faz.net/rss/aktuell/politik/ausland/", "德国", "欧洲", "de", "news", "A"));

        sources.add(createSource("El Pais", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/section/internacional/portada", "西班牙", "欧洲", "es", "news", "A"));
        sources.add(createSource("El Mundo", "https://www.elmundo.es/rss/internacional.xml", "西班牙", "欧洲", "es", "news", "B"));

        sources.add(createSource("La Repubblica", "https://www.repubblica.it/rss/esteri/rss2.0.xml", "意大利", "欧洲", "it", "news", "B"));
        sources.add(createSource("Corriere della Sera", "https://www.corriere.it/rss/esteri.xml", "意大利", "欧洲", "it", "news", "B"));

        sources.add(createSource("TASS Russian News", "https://tass.com/rss/v2.xml", "俄罗斯", "欧洲", "en", "news", "B"));
        sources.add(createSource("Russia Today", "https://www.rt.com/rss/", "俄罗斯", "欧洲", "en", "news", "C"));

        sources.add(createSource("Xinhua English", "https://www.xinhuanet.com/english/rss/world.xml", "中国", "亚洲", "en", "news", "A"));
        sources.add(createSource("China Daily", "https://www.chinadaily.com.cn/rss/world_rss.xml", "中国", "亚洲", "en", "news", "A"));
        sources.add(createSource("Global Times", "https://www.globaltimes.cn/rss/outbrain.xml", "中国", "亚洲", "en", "news", "B"));

        sources.add(createSource("South China Morning Post", "https://www.scmp.com/rss/91/feed", "香港", "亚洲", "en", "news", "A"));
        sources.add(createSource("Taipei Times", "https://www.taipeitimes.com/rss.xml", "台湾", "亚洲", "en", "news", "B"));

        sources.add(createSource("The Straits Times", "https://www.straitstimes.com/news/world/rss.xml", "新加坡", "亚洲", "en", "news", "B"));
        sources.add(createSource("Channel NewsAsia", "https://www.channelnewsasia.com/rssfeeds/8395984", "新加坡", "亚洲", "en", "news", "B"));

        sources.add(createSource("The Australian", "https://www.theaustralian.com.au/news/world/rss", "澳大利亚", "大洋洲", "en", "news", "A"));
        sources.add(createSource("ABC Australia", "https://www.abc.net.au/news/feed/51120/rss.xml", "澳大利亚", "大洋洲", "en", "news", "A"));
        sources.add(createSource("Sydney Morning Herald", "https://www.smh.com.au/rss/world.xml", "澳大利亚", "大洋洲", "en", "news", "B"));

        sources.add(createSource("Toronto Star", "https://www.thestar.com/content/thestar/feed.RSSManagerServlet.articles.world.focal/WorldNews", "加拿大", "北美", "en", "news", "B"));
        sources.add(createSource("CBC World", "https://www.cbc.ca/webfeed/rss/rss-world", "加拿大", "北美", "en", "news", "A"));
        sources.add(createSource("Globe and Mail", "https://www.theglobeandmail.com/arc/outboundfeeds/rss/category/world/", "加拿大", "北美", "en", "news", "A"));

        sources.add(createSource("Gulf News", "https://gulfnews.com/rss/world", "阿联酋", "中东", "en", "news", "B"));
        sources.add(createSource("Khaleej Times", "https://www.khaleejtimes.com/rss/world", "阿联酋", "中东", "en", "news", "B"));

        sources.add(createSource("Jerusalem Post", "https://www.jpost.com/rss/rssfeedsheadlines", "以色列", "中东", "en", "news", "B"));
        sources.add(createSource("Haaretz", "https://www.haaretz.com/srv/haaretz-latest-headlines", "以色列", "中东", "en", "news", "B"));

        sources.add(createSource("Daily Sabah", "https://www.dailysabah.com/rssFeed/world", "土耳其", "中东", "en", "news", "C"));
        sources.add(createSource("Hurriyet Daily News", "https://www.hurriyetdailynews.com/rss.aspx", "土耳其", "中东", "en", "news", "C"));

        sources.add(createSource("Egypt Independent", "https://www.egyptindependent.com/rss-feed", "埃及", "非洲", "en", "news", "C"));
        sources.add(createSource("Daily News Egypt", "https://dailynewsegypt.com/feed/", "埃及", "非洲", "en", "news", "C"));

        sources.add(createSource("News24", "https://feeds.news24.com/articles/news24/World/rss", "南非", "非洲", "en", "news", "B"));
        sources.add(createSource("Mail & Guardian", "https://mg.co.za/feed/", "南非", "非洲", "en", "news", "C"));

        sources.add(createSource("Buenos Aires Herald", "https://buenosairesherald.com/feed/", "阿根廷", "南美", "en", "news", "C"));
        sources.add(createSource("The Buenos Aires Times", "https://www.batimes.com.ar/rss/feed", "阿根廷", "南美", "en", "news", "C"));

        sources.add(createSource("O Globo", "https://oglobo.globo.com/rss.xml?modulo=2546", "巴西", "南美", "pt", "news", "B"));
        sources.add(createSource("Folha de S.Paulo", "https://feeds.folha.uol.com.br/folha/mundo/rss091.xml", "巴西", "南美", "pt", "news", "B"));

        sources.add(createSource("El Mercurio", "https://www.emol.com/rss/rss.xml", "智利", "南美", "es", "news", "C"));

        sources.add(createSource("TechCrunch", "https://techcrunch.com/feed/", "美国", "北美", "en", "tech", "A"));
        sources.add(createSource("The Verge", "https://www.theverge.com/rss/index.xml", "美国", "北美", "en", "tech", "A"));
        sources.add(createSource("Wired", "https://www.wired.com/feed/rss", "美国", "北美", "en", "tech", "A"));
        sources.add(createSource("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "美国", "北美", "en", "tech", "A"));
        sources.add(createSource("Engadget", "https://www.engadget.com/rss.xml", "美国", "北美", "en", "tech", "A"));
        sources.add(createSource("Gizmodo", "https://gizmodo.com/rss", "美国", "北美", "en", "tech", "B"));

        sources.add(createSource("Bloomberg Technology", "https://www.bloomberg.com/feed/podcast/bloomberg-technology.xml", "美国", "北美", "en", "tech", "A"));
        sources.add(createSource("MIT Technology Review", "https://www.technologyreview.com/feed/", "美国", "北美", "en", "tech", "A"));

        sources.add(createSource("Reuters Business", "https://www.reutersagency.com/feed/?taxonomy=best-topics&post_type=best", "美国", "北美", "en", "finance", "A"));
        sources.add(createSource("Bloomberg", "https://www.bloomberg.com/feed/news/rss/bloomberg", "美国", "北美", "en", "finance", "A"));
        sources.add(createSource("Financial Times", "https://www.ft.com/rss/home", "英国", "欧洲", "en", "finance", "A"));
        sources.add(createSource("Forbes", "https://www.forbes.com/real-time/feed2/", "美国", "北美", "en", "finance", "A"));
        sources.add(createSource("MarketWatch", "https://www.marketwatch.com/rss/topstories", "美国", "北美", "en", "finance", "A"));
        sources.add(createSource("CNBC", "https://www.cnbc.com/id/100727362/device/rss/rss.html", "美国", "北美", "en", "finance", "A"));

        sources.add(createSource("ESPN", "https://www.espn.com/espn/rss/news", "美国", "北美", "en", "sports", "A"));
        sources.add(createSource("BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "英国", "欧洲", "en", "sports", "A"));
        sources.add(createSource("Sky Sports", "https://www.skysports.com/rss/12040", "英国", "欧洲", "en", "sports", "A"));
        sources.add(createSource("Sports Illustrated", "https://www.si.com/.rss/full", "美国", "北美", "en", "sports", "B"));

        sources.add(createSource("Entertainment Weekly", "https://ew.com/feed/", "美国", "北美", "en", "entertainment", "B"));
        sources.add(createSource("Variety", "https://variety.com/feed/", "美国", "北美", "en", "entertainment", "A"));
        sources.add(createSource("Hollywood Reporter", "https://www.hollywoodreporter.com/feed/", "美国", "北美", "en", "entertainment", "A"));
        sources.add(createSource("E! Online", "https://www.eonline.com/syndication/feeds/rssfeeds/topstories.xml", "美国", "北美", "en", "entertainment", "B"));

        sources.add(createSource("Nature", "https://www.nature.com/nature.rss", "英国", "欧洲", "en", "science", "A"));
        sources.add(createSource("Science Daily", "https://www.sciencedaily.com/rss/all.xml", "美国", "北美", "en", "science", "A"));
        sources.add(createSource("Scientific American", "https://www.scientificamerican.com/rss/news/", "美国", "北美", "en", "science", "A"));
        sources.add(createSource("New Scientist", "https://www.newscientist.com/feed/home/", "英国", "欧洲", "en", "science", "A"));

        sources.add(createSource("Health News", "https://www.healthnews.com/rss.xml", "美国", "北美", "en", "health", "B"));
        sources.add(createSource("WebMD", "https://rssfeeds.webmd.com/rss/rss.aspx?RSSNewsRecordID=@", "美国", "北美", "en", "health", "B"));
        sources.add(createSource("Medical News Today", "https://www.medicalnewstoday.com/rss", "美国", "北美", "en", "health", "B"));

        sources.add(createSource("National Geographic", "https://www.nationalgeographic.com/feed/", "美国", "北美", "en", "environment", "A"));
        sources.add(createSource("Climate Home News", "https://www.climatechangenews.com/feed/", "英国", "欧洲", "en", "environment", "B"));

        sources.add(createSource("Politico", "https://rss.politico.com/politics-news.xml", "美国", "北美", "en", "politics", "A"));
        sources.add(createSource("The Hill", "https://thehill.com/feed/", "美国", "北美", "en", "politics", "A"));

        sources.add(createSource("The Economist", "https://www.economist.com/rss", "英国", "欧洲", "en", "news", "A"));
        sources.add(createSource("Foreign Affairs", "https://www.foreignaffairs.com/rss.xml", "美国", "北美", "en", "politics", "A"));
        sources.add(createSource("Foreign Policy", "https://foreignpolicy.com/feed/", "美国", "北美", "en", "politics", "A"));

        sources.add(createSource("Vox", "https://www.vox.com/rss/index.xml", "美国", "北美", "en", "news", "B"));
        sources.add(createSource("Buzzfeed News", "https://www.buzzfeed.com/world.xml", "美国", "北美", "en", "news", "C"));
        sources.add(createSource("HuffPost World", "https://www.huffpost.com/section/world/feed", "美国", "北美", "en", "news", "C"));

        sources.add(createSource("NPR World", "https://feeds.npr.org/1004/rss.xml", "美国", "北美", "en", "news", "A"));
        sources.add(createSource("PRI The World", "https://www.pri.org/rss.xml", "美国", "北美", "en", "news", "B"));

        sources.add(createSource("World Politics Review", "https://www.worldpoliticsreview.com/rss-feeds", "美国", "北美", "en", "politics", "B"));
        sources.add(createSource("Council on Foreign Relations", "https://www.cfr.org/rss/feed/analysis", "美国", "北美", "en", "politics", "A"));

        sources.add(createSource("Brookings Institution", "https://www.brookings.edu/feed/", "美国", "北美", "en", "politics", "A"));
        sources.add(createSource("Carnegie Endowment", "https://carnegieendowment.org/rss/all.xml", "美国", "北美", "en", "politics", "A"));

        sources.add(createSource("Chatham House", "https://www.chathamhouse.org/rss.xml", "英国", "欧洲", "en", "politics", "A"));
        sources.add(createSource("International Crisis Group", "https://www.crisisgroup.org/rss.xml", "比利时", "欧洲", "en", "politics", "A"));

        sources.add(createSource("The Diplomat", "https://thediplomat.com/feed/", "美国", "北美", "en", "politics", "B"));
        sources.add(createSource("Asia Times", "https://asiatimes.com/feed/", "香港", "亚洲", "en", "news", "C"));

        sources.add(createSource("Quartz", "https://qz.com/feed/", "美国", "北美", "en", "news", "B"));
        sources.add(createSource("Axios", "https://api.axios.com/feed/", "美国", "北美", "en", "news", "B"));

        sources.add(createSource("The Conversation", "https://theconversation.com/global/world?format=rss", "澳大利亚", "大洋洲", "en", "news", "B"));

        sources.add(createSource("Radio Free Europe", "https://www.rferl.org/api/zrqiteuuir", "捷克", "欧洲", "en", "news", "B"));
        sources.add(createSource("Radio Free Asia", "https://www.rfa.org/english/rss", "美国", "北美", "en", "news", "C"));

        sources.add(createSource("Middle East Eye", "https://www.middleeasteye.net/rss", "英国", "欧洲", "en", "news", "C"));
        sources.add(createSource("Middle East Monitor", "https://www.middleeastmonitor.com/rss/", "英国", "欧洲", "en", "news", "C"));

        sources.add(createSource("Africanews", "https://www.africanews.com/rss/news/topstories.rss", "刚果", "非洲", "en", "news", "C"));
        sources.add(createSource("The Africa Report", "https://www.theafricareport.com/rss/feed/", "法国", "欧洲", "en", "news", "C"));

        sources.add(createSource("Nikkei Asia", "https://asia.nikkei.com/rss/feed", "日本", "亚洲", "en", "news", "A"));
        sources.add(createSource("Asia News Network", "https://asianews.network/feed/", "泰国", "亚洲", "en", "news", "C"));

        sources.add(createSource("Jakarta Post", "https://www.thejakartapost.com/rss/feed", "印度尼西亚", "亚洲", "en", "news", "B"));
        sources.add(createSource("Bangkok Post", "https://www.bangkokpost.com/rss/data/latest.xml", "泰国", "亚洲", "en", "news", "B"));
        sources.add(createSource("Vietnam News", "https://vietnamnews.vn/rss/international.rss", "越南", "亚洲", "en", "news", "C"));
        sources.add(createSource("Philippine Daily Inquirer", "https://globalnation.inquirer.net/feed", "菲律宾", "亚洲", "en", "news", "C"));
        sources.add(createSource("Malay Mail", "https://www.malaymail.com/feed/rss/malaysia", "马来西亚", "亚洲", "en", "news", "C"));

        sources.add(createSource("Dawn", "https://www.dawn.com/feed", "巴基斯坦", "亚洲", "en", "news", "B"));
        sources.add(createSource("The Express Tribune", "https://tribune.com.pk/rss/world", "巴基斯坦", "亚洲", "en", "news", "C"));

        sources.add(createSource("The Kathmandu Post", "https://kathmandupost.com/rss", "尼泊尔", "亚洲", "en", "news", "C"));
        sources.add(createSource("The Daily Star", "https://www.thedailystar.net/rss.xml", "孟加拉国", "亚洲", "en", "news", "C"));

        sources.add(createSource("Iran International", "https://www.iranintl.com/rss", "英国", "欧洲", "en", "news", "C"));

        sources.add(createSource("Ukraine Pravda", "https://www.pravda.com.ua/eng/rss/", "乌克兰", "欧洲", "en", "news", "C"));

        sources.add(createSource("Belarus Digest", "https://belarusdigest.com/feed/", "白俄罗斯", "欧洲", "en", "news", "C"));

        sources.add(createSource("Baltic Times", "https://www.baltictimes.com/rss/", "拉脱维亚", "欧洲", "en", "news", "C"));

        sources.add(createSource("Prague Post", "https://www.praguepost.com/feed/", "捷克", "欧洲", "en", "news", "C"));
        sources.add(createSource("Warsaw Business Journal", "https://wbj.pl/rss/", "波兰", "欧洲", "en", "news", "C"));

        sources.add(createSource("Budapest Times", "https://budapesttimes.hu/feed/", "匈牙利", "欧洲", "en", "news", "C"));
        sources.add(createSource("Romania Insider", "https://www.romania-insider.com/rss", "罗马尼亚", "欧洲", "en", "news", "C"));

        sources.add(createSource("Athens News", "https://www.athensnews.gr/rss", "希腊", "欧洲", "en", "news", "C"));

        sources.add(createSource("Nordic Page", "https://nordicpagetoday.com/feed/", "北欧", "欧洲", "en", "news", "C"));
        sources.add(createSource("The Local Sweden", "https://www.thelocal.se/rss/", "瑞典", "欧洲", "en", "news", "C"));
        sources.add(createSource("The Local Norway", "https://www.thelocal.no/rss/", "挪威", "欧洲", "en", "news", "C"));
        sources.add(createSource("The Local Denmark", "https://www.thelocal.dk/rss/", "丹麦", "欧洲", "en", "news", "C"));

        sources.add(createSource("Irish Times", "https://www.irishtimes.com/rss/world", "爱尔兰", "欧洲", "en", "news", "B"));
        sources.add(createSource("Irish Independent", "https://www.independent.ie/world-news/rss", "爱尔兰", "欧洲", "en", "news", "C"));

        sources.add(createSource("Swiss Info", "https://www.swissinfo.ch/eng/rss/world.xml", "瑞士", "欧洲", "en", "news", "B"));
        sources.add(createSource("Neue Zürcher Zeitung", "https://www.nzz.ch/international.rss", "瑞士", "欧洲", "de", "news", "A"));

        sources.add(createSource("Vienna Times", "https://vienna.times.com/feed/", "奥地利", "欧洲", "en", "news", "C"));

        sources.add(createSource("Brussels Times", "https://www.brusselstimes.com/rss", "比利时", "欧洲", "en", "news", "C"));
        sources.add(createSource("NL Times", "https://nltimes.nl/rss", "荷兰", "欧洲", "en", "news", "C"));

        sources.add(createSource("Portugal News", "https://www.theportugalnews.com/rss", "葡萄牙", "欧洲", "en", "news", "C"));

        sources.add(createSource("Balkan Insight", "https://balkaninsight.com/feed/", "巴尔干", "欧洲", "en", "news", "C"));

        sources.add(createSource("New Zealand Herald", "https://www.nzherald.co.nz/arc/outboundfeeds/rss/section/nz/world/", "新西兰", "大洋洲", "en", "news", "B"));
        sources.add(createSource("Stuff", "https://www.stuff.co.nz/rss/world", "新西兰", "大洋洲", "en", "news", "B"));

        sources.add(createSource("Fiji Times", "https://www.fijitimes.com.fj/feed/", "斐济", "大洋洲", "en", "news", "C"));

        sources.add(createSource("Mexico News Daily", "https://mexiconewsdaily.com/feed/", "墨西哥", "北美", "en", "news", "C"));

        sources.add(createSource("Jamaica Gleaner", "https://jamaica-gleaner.com/feed", "牙买加", "北美", "en", "news", "C"));
        sources.add(createSource("Caribbean News Now", "https://www.caribbean-newsnow.com/rss/", "加勒比", "北美", "en", "news", "C"));

        sources.add(createSource("Kenya Standard", "https://www.standardmedia.co.ke/rss/headlines.php", "肯尼亚", "非洲", "en", "news", "C"));
        sources.add(createSource("Daily Nation", "https://nation.africa/kenya/rss", "肯尼亚", "非洲", "en", "news", "C"));

        sources.add(createSource("The Guardian Nigeria", "https://guardian.ng/feed/", "尼日利亚", "非洲", "en", "news", "C"));
        sources.add(createSource("Punch Nigeria", "https://punchng.com/feed/", "尼日利亚", "非洲", "en", "news", "C"));

        sources.add(createSource("Ethiopia Reporter", "https://www.ethiopianreporter.com/rss.xml", "埃塞俄比亚", "非洲", "en", "news", "C"));

        sources.add(createSource("Morocco World News", "https://www.moroccoworldnews.com/feed", "摩洛哥", "非洲", "en", "news", "C"));

        sources.add(createSource("Tunisia Live", "https://www.tunisia-live.net/feed/", "突尼斯", "非洲", "en", "news", "C"));

        sources.add(createSource("Colombia Reports", "https://colombiareports.com/feed/", "哥伦比亚", "南美", "en", "news", "C"));
        sources.add(createSource("Peru Reports", "https://perureports.com/feed/", "秘鲁", "南美", "en", "news", "C"));
        sources.add(createSource("Venezuela Analysis", "https://venezuelanalysis.com/rss.xml", "委内瑞拉", "南美", "en", "news", "C"));

        return sources;
    }

    /**
     * 创建RSS源实体
     *
     * @param name RSS源名称
     * @param url RSS URL
     * @param country 国家
     * @param region 地区
     * @param language 语言
     * @param category 分类
     * @param authorityLevel 权威等级
     * @return RSS源实体
     */
    private RssSource createSource(String name, String url, String country, String region,
                                   String language, String category, String authorityLevel) {
        RssSource source = new RssSource();
        source.setName(name);
        source.setUrl(url);
        source.setCountry(country);
        source.setRegion(region);
        source.setLanguage(language);
        source.setCategory(category);
        source.setAuthorityLevel(authorityLevel);
        source.setActive(true);
        source.setFailCount(0);
        source.setTotalArticles(0L);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        return source;
    }
}
