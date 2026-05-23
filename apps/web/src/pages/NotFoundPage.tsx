import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';

export default function NotFoundPage() {
  return (
    <div className="min-h-screen bg-bg-base flex items-center justify-center px-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="text-center"
      >
        <p className="text-8xl font-black text-gradient-brand mb-4">404</p>
        <h1 className="text-2xl font-bold text-text-primary mb-2">Page not found</h1>
        <p className="text-text-secondary text-sm mb-8">
          The page you're looking for doesn't exist or has been moved.
        </p>
        <Link to="/problems" className="btn-primary">
          Back to Problems
        </Link>
      </motion.div>
    </div>
  );
}
