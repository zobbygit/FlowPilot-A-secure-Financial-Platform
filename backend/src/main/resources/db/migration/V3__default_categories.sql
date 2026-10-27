-- Platform default categories (user_id NULL). Users can add their own later.
INSERT INTO categories (name, kind, color, sort_order) VALUES
  ('Food & Dining',     'EXPENSE', '#8b5cf6', 10),
  ('Shopping',          'EXPENSE', '#a78bfa', 20),
  ('Transport',         'EXPENSE', '#22d3ee', 30),
  ('Bills & Utilities', 'EXPENSE', '#f472b6', 40),
  ('Entertainment',     'EXPENSE', '#c084fc', 50),
  ('Health',            'EXPENSE', '#34d399', 60),
  ('Others',            'EXPENSE', '#6366f1', 90),
  ('Salary',            'INCOME',  '#34d399', 10),
  ('Freelance',         'INCOME',  '#22d3ee', 20),
  ('Other income',      'INCOME',  '#a78bfa', 90);
