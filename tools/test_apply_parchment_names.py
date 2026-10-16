import unittest

from apply_parchment_names import rename


class RenameTest(unittest.TestCase):
    def test_identifiers_only(self):
        source = '''void move(long p_1_) {
    use(p_1_, p_10_, p_1_suffix); // p_1_
    /* p_1_ */ String text = "p_1_ \\" p_1_";
    char quote = '\\'';
    String block = """
        p_1_
        """;
}'''
        expected = source.replace('long p_1_', 'long pX').replace('use(p_1_,', 'use(pX,')
        self.assertEqual(expected, rename(source, {'p_1_': 'pX'}))

    def test_idempotent(self):
        names = {'p_1_': 'pX'}
        source = 'return p_1_ + p_2_;'
        updated = rename(source, names)
        self.assertEqual('return pX + p_2_;', updated)
        self.assertEqual(updated, rename(updated, names))


if __name__ == '__main__':
    unittest.main()
