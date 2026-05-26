-- V3: Align existing categories with frontend filter chips and add more problems
-- Frontend CATEGORIES = ['All','Storage','Compute','Messaging','Search','Social','Financial']

UPDATE sysdesign_problems SET category = 'Compute'  WHERE slug IN ('design-url-shortener', 'design-rate-limiter');
UPDATE sysdesign_problems SET category = 'Social'   WHERE slug = 'design-twitter';
UPDATE sysdesign_problems SET category = 'Compute'  WHERE slug = 'design-netflix';
UPDATE sysdesign_problems SET category = 'Storage'  WHERE slug = 'design-distributed-cache';

-- ── New problems ───────────────────────────────────────────────────────────────

INSERT INTO sysdesign_problems (slug, title, difficulty, category, description_md, requirements, is_published) VALUES

-- Storage
('design-s3',
 'Design Amazon S3',
 'HARD', 'Storage',
 E'## Design Amazon S3\n\nDesign a globally distributed object storage service similar to Amazon S3.\n\nConsider how to store billions of objects ranging from a few bytes to terabytes, ensure 11 nines of durability, and serve petabytes of data per day.',
 ARRAY[
   'Store objects up to 5 TB',
   '11 nines durability (99.999999999%)',
   'Versioning and lifecycle policies',
   'Multi-region replication',
   'Pre-signed URL access control',
   '10 billion objects stored'
 ],
 true),

('design-hdfs',
 'Design a Distributed File System',
 'HARD', 'Storage',
 E'## Design a Distributed File System\n\nDesign a distributed file system similar to HDFS (Hadoop Distributed File System) that can store and process large datasets across commodity hardware.\n\nFocus on fault tolerance, data locality, and batch processing support.',
 ARRAY[
   'Store files up to petabytes',
   'Replicate blocks across 3 nodes',
   'Handle NameNode single point of failure',
   'Rack-aware block placement',
   'Append-only write semantics'
 ],
 true),

-- Compute
('design-kubernetes',
 'Design a Container Orchestration System',
 'HARD', 'Compute',
 E'## Design a Container Orchestration System\n\nDesign a system similar to Kubernetes that can schedule and manage containerized workloads across a cluster of thousands of machines.\n\nFocus on the scheduler, service discovery, health checking, and rolling deployments.',
 ARRAY[
   'Schedule 100,000 containers across 10,000 nodes',
   'Zero-downtime rolling deployments',
   'Service discovery and load balancing',
   'Self-healing — restart failed containers',
   'Resource quota enforcement'
 ],
 true),

('design-task-scheduler',
 'Design a Distributed Task Scheduler',
 'MEDIUM', 'Compute',
 E'## Design a Distributed Task Scheduler\n\nDesign a distributed job scheduler that can run millions of cron-like tasks reliably across a fleet of workers.\n\nThink about deduplication, retry with backoff, priority queues, and observability.',
 ARRAY[
   '10 million scheduled jobs',
   'At-least-once execution guarantee',
   'Sub-second scheduling precision',
   'Priority queue support',
   'Retry with exponential backoff'
 ],
 true),

-- Messaging
('design-kafka',
 'Design a Distributed Message Queue',
 'HARD', 'Messaging',
 E'## Design a Distributed Message Queue\n\nDesign a high-throughput distributed message queue similar to Apache Kafka.\n\nFocus on log-structured storage, consumer groups, partition rebalancing, and exactly-once delivery semantics.',
 ARRAY[
   'Write 10 million messages/second',
   'Message retention for 7 days',
   'Consumer group rebalancing',
   'Exactly-once delivery option',
   'Partition-level ordering guarantees'
 ],
 true),

-- Search
('design-typeahead',
 'Design a Typeahead Search Service',
 'MEDIUM', 'Search',
 E'## Design a Typeahead Search Service\n\nDesign the typeahead/autocomplete backend that powers search boxes on a social platform with 500 million users.\n\nFocus on trie construction, caching hot prefixes, and personalised ranking.',
 ARRAY[
   'Return top-10 results in < 50 ms',
   'Handle 100,000 QPS at peak',
   'Personalised suggestions per user',
   'Trending prefix detection',
   'Incremental trie updates without downtime'
 ],
 true),

('design-elasticsearch-clone',
 'Design a Full-Text Search Engine',
 'HARD', 'Search',
 E'## Design a Full-Text Search Engine\n\nDesign a scalable full-text search engine that can index billions of documents and serve complex queries with faceting, highlighting, and relevance ranking.\n\nInspired by Elasticsearch internals.',
 ARRAY[
   'Index 1 billion documents',
   'Query latency < 100 ms at p99',
   'Boolean, phrase, and fuzzy queries',
   'Near-real-time indexing (< 1 s delay)',
   'Distributed shard allocation'
 ],
 true),

-- Social
('design-instagram',
 'Design Instagram',
 'HARD', 'Social',
 E'## Design Instagram\n\nDesign a photo-sharing social platform with 1 billion users.\n\nFocus on the feed generation pipeline, CDN strategy for media, follower graph storage, and the push vs pull trade-off for home feed.',
 ARRAY[
   '1 billion monthly active users',
   'Home feed renders in < 200 ms',
   'Media upload → CDN in < 5 s',
   'Follower graph with 500M edges',
   'Stories expire after 24 hours'
 ],
 true),

('design-youtube',
 'Design YouTube',
 'HARD', 'Social',
 E'## Design YouTube\n\nDesign a large-scale video platform that handles video uploads, transcoding, storage, and global delivery.\n\nAddress the upload pipeline, adaptive bitrate streaming, view count consistency, and recommendation signals.',
 ARRAY[
   '500 hours of video uploaded per minute',
   'Adaptive bitrate: 360p → 4K',
   'CDN edge caching strategy',
   'View count eventual consistency',
   '100 million daily active viewers'
 ],
 true),

-- Financial
('design-payment-system',
 'Design a Payment Processing System',
 'HARD', 'Financial',
 E'## Design a Payment Processing System\n\nDesign a payment processing platform similar to Stripe that handles card authorisation, settlement, refunds, and payout to merchants.\n\nFocus on idempotency, double-spend prevention, PCI compliance boundaries, and reconciliation.',
 ARRAY[
   'Process 10,000 transactions per second',
   'Idempotent payment API',
   'Double-spend prevention',
   'Sub-second authorisation response',
   'Automated reconciliation with card networks'
 ],
 true),

('design-stock-exchange',
 'Design a Stock Exchange',
 'HARD', 'Financial',
 E'## Design a Stock Exchange\n\nDesign the core matching engine and order book for a stock exchange that must process millions of orders per second with microsecond-level latency and strict ordering guarantees.\n\nFocus on order book data structures, matching algorithms, and market data distribution.',
 ARRAY[
   'Process 5 million orders/second',
   'FIFO price-time priority matching',
   'Market data broadcast < 1 ms latency',
   'Full audit log for regulatory compliance',
   'Circuit-breaker halts on abnormal price moves'
 ],
 true),

('design-crypto-wallet',
 'Design a Crypto Wallet Service',
 'MEDIUM', 'Financial',
 E'## Design a Crypto Wallet Service\n\nDesign a custodial cryptocurrency wallet service used by an exchange with 50 million users.\n\nAddress hot/cold wallet architecture, transaction signing security, withdrawal flow, and blockchain confirmation tracking.',
 ARRAY[
   '50 million user wallets',
   'Hot wallet < 2% of total funds',
   'Multi-signature cold storage',
   'Blockchain confirmation tracking',
   'Rate-limited and 2FA-gated withdrawals'
 ],
 true)

ON CONFLICT (slug) DO NOTHING;
